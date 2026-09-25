package com.wickedcoder.wifilens.core.wifi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** Progress of one [downloadSpeedFlow] run. */
sealed interface SpeedTestUpdate {
    /** [mbps] is the running average so far; [fraction] is how much of the time budget is used (0..1). */
    data class Running(val mbps: Float, val fraction: Float) : SpeedTestUpdate
    data class Finished(val mbps: Float) : SpeedTestUpdate
    data class Failed(val reason: String) : SpeedTestUpdate
}

internal const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=50000000"
private const val TICK_MS = 250L
private const val BITS_PER_MEGABIT = 1_000_000.0

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
): Flow<SpeedTestUpdate> = channelFlow {
    val totalBytes = AtomicLong(0)
    val firstByteNs = AtomicLong(0)
    val connections = ConcurrentLinkedQueue<HttpURLConnection>()

    fun currentMbps(nowNs: Long): Float {
        val first = firstByteNs.get()
        if (first == 0L) return 0f
        val seconds = ((nowNs - first) / 1e9).coerceAtLeast(0.001)
        return (totalBytes.get() * 8 / seconds / BITS_PER_MEGABIT).toFloat()
    }

    val startNs = System.nanoTime()
    val workers = List(streams) {
        launch(Dispatchers.IO) {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    useCaches = false
                }
                connections.add(connection)
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
            } catch (_: Exception) { // IOException, or a SecurityException/RuntimeException from the platform stack
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
        send(SpeedTestUpdate.Failed("Couldn't reach the speed test server. Check that your Wi-Fi has internet access."))
    } else {
        send(SpeedTestUpdate.Finished(currentMbps(endNs)))
    }
}
