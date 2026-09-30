package com.wickedcoder.wifilens.core.wifi

import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=50000000"
private const val TICK_MS = 250L
private const val BITS_PER_MEGABIT = 1_000_000.0
private val HTTP_OK_RANGE = 200..299

/** HTTP statuses a filtering proxy or firewall answers with: forbidden, proxy auth required, unavailable for legal reasons. */
private val BLOCKING_STATUSES = setOf(403, 407, 451)

private class HttpStatusException(val status: Int) : IOException("HTTP $status")

/** Connects and checks the status; a non-2xx answer throws [HttpStatusException] so the worker records it. */
private fun openDownload(url: String, connections: ConcurrentLinkedQueue<HttpURLConnection>): HttpURLConnection {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        useCaches = false
    }
    connections.add(connection)
    val status = connection.responseCode
    if (status !in HTTP_OK_RANGE) throw HttpStatusException(status)
    return connection
}

/** The error is kept in [SpeedTestUpdate.Failed.reason] for the log; [SpeedTestUpdate.Failed.blocked] picks the message. */
private fun failure(error: Exception?): SpeedTestUpdate.Failed {
    val cause = error?.let { " (${it::class.java.simpleName}: ${it.message})" }.orEmpty()
    return SpeedTestUpdate.Failed("Couldn't reach the speed test server$cause", blocked = error?.isBlockedByNetwork() == true)
}

/**
 * The server was found and a connection started, but the network cut it or refused it. No DNS answer, no route, a
 * refused port or a timeout mean no internet (or a server outage) instead.
 */
private fun Exception.isBlockedByNetwork(): Boolean = when (this) {
    is HttpStatusException -> status in BLOCKING_STATUSES
    is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> false
    else -> this is IOException
}

/**
 * Measures real download throughput by pulling data from Cloudflare's public speed-test endpoint over
 * [streams] parallel connections for at most [durationMs] ([url] is overridable for tests). Parallel streams because a single TCP
 * connection often can't saturate a fast Wi-Fi link, which would make a good router look slow.
 *
 * The clock starts at the first received byte so DNS/TLS setup isn't counted as slow bandwidth.
 * Uses up to roughly `streams * link speed * durationMs` of data, i.e. tens of MB on a typical home link.
 */
fun downloadSpeedFlow(
    durationMs: Long = 8_000,
    streams: Int = 4,
    url: String = DEFAULT_DOWNLOAD_URL,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Flow<SpeedTestUpdate> = channelFlow {
    val totalBytes = AtomicLong(0)
    val firstByteNs = AtomicLong(0)
    val connections = ConcurrentLinkedQueue<HttpURLConnection>()
    val firstError = AtomicReference<Exception?>(null)

    fun currentMbps(nowNs: Long): Float {
        val first = firstByteNs.get()
        if (first == 0L) return 0f
        val seconds = ((nowNs - first) / 1e9).coerceAtLeast(0.001)
        return (totalBytes.get() * 8 / seconds / BITS_PER_MEGABIT).toFloat()
    }

    val startNs = System.nanoTime()
    val workers = List(streams) {
        launch(ioDispatcher) {
            try {
                val connection = openDownload(url, connections)
                val buffer = ByteArray(64 * 1024)
                connection.inputStream.use { input ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        firstByteNs.compareAndSet(0, System.nanoTime())
                        totalBytes.addAndGet(read.toLong())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                firstError.compareAndSet(null, e)
                // IOException, or a SecurityException/RuntimeException from the platform stack
                // Counted by totalBytes: if every stream failed before any byte arrived we report
                // Failed below; a stream cut off by the timeout disconnect is expected.
            }
        }
    }

    val ticker = launch {
        while (isActive) {
            delay(TICK_MS)
            val now = System.nanoTime()
            val fraction = ((now - startNs) / 1e6 / durationMs).toFloat().coerceIn(0f, 1f)
            send(SpeedTestUpdate.Running(currentMbps(now), fraction))
        }
    }

    val endNs: Long
    try {
        withTimeoutOrNull(durationMs) { workers.joinAll() }
        endNs = System.nanoTime()
    } finally {
        // A blocking read can't be cancelled by the coroutine, so disconnecting is what actually stops it
        // — also when the collector goes away mid-test (rotation-safe, tab-safe).
        connections.forEach { it.disconnect() }
        workers.forEach { it.cancel() }
        ticker.cancel()
    }

    if (totalBytes.get() == 0L) {
        send(failure(firstError.get()))
    } else {
        send(SpeedTestUpdate.Finished(currentMbps(endNs)))
    }
}
