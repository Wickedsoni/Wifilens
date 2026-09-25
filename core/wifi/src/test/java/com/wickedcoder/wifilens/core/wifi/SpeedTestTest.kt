package com.wickedcoder.wifilens.core.wifi

import com.sun.net.httpserver.HttpServer
import com.wickedcoder.wifilens.core.model.SpeedTestUpdate
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Runs [downloadSpeedFlow] against a throwaway local HTTP server instead of the real internet. */
class SpeedTestTest {
    private lateinit var server: HttpServer

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun serve(path: String, status: Int, body: ByteArray) {
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
    }

    @Test
    fun `successful download finishes with a positive speed`() = runBlocking {
        serve("/ok", 200, ByteArray(2_000_000))

        val updates = downloadSpeedFlow(durationMs = 3_000, streams = 2, url = "$baseUrl/ok").toList()

        val last = updates.last()
        assertTrue("expected Finished but was $last", last is SpeedTestUpdate.Finished)
        assertTrue((last as SpeedTestUpdate.Finished).mbps > 0f)
    }

    @Test
    fun `server error is reported as Failed`() = runBlocking {
        serve("/boom", 500, ByteArray(0))

        val updates = downloadSpeedFlow(durationMs = 2_000, streams = 2, url = "$baseUrl/boom").toList()

        assertTrue(updates.last() is SpeedTestUpdate.Failed)
    }

    @Test
    fun `unreachable server is reported as Failed`() = runBlocking {
        server.start()
        val deadUrl = "$baseUrl/x"
        server.stop(0)

        val updates = downloadSpeedFlow(durationMs = 2_000, streams = 1, url = deadUrl).toList()

        assertTrue(updates.last() is SpeedTestUpdate.Failed)
    }

    @Test
    fun `empty 200 response is Failed rather than reporting 0 Mbps`() = runBlocking {
        serve("/empty", 200, ByteArray(0))

        val updates = downloadSpeedFlow(durationMs = 2_000, streams = 1, url = "$baseUrl/empty").toList()

        assertTrue(updates.last() is SpeedTestUpdate.Failed)
    }

    @Test
    fun `flow ends with exactly one terminal update`() = runBlocking {
        serve("/ok", 200, ByteArray(500_000))

        val updates = downloadSpeedFlow(durationMs = 2_000, streams = 1, url = "$baseUrl/ok").toList()

        val terminal = updates.count { it is SpeedTestUpdate.Finished || it is SpeedTestUpdate.Failed }
        assertEquals(1, terminal)
    }
}
