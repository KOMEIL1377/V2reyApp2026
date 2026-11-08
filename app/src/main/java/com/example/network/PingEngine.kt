package com.example.network

import com.example.model.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object PingEngine {

    /**
     * Performs a direct TCP socket handshake to node's server and port
     * Returns ping latency in milliseconds, or -1 if unreachable/timeout.
     */
    suspend fun testLatency(node: ProxyNode, timeoutMs: Int = 3000): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                val address = InetSocketAddress(node.server, node.port)
                socket.connect(address, timeoutMs)
                val duration = System.currentTimeMillis() - startTime
                if (duration <= 0) 1L else duration
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Checks latency of all nodes with controlled concurrency
     */
    suspend fun testAll(
        nodes: List<ProxyNode>,
        onProgress: (nodeId: String, ping: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Use chunks to avoid socket exhaustion
        nodes.chunked(6).forEach { chunk ->
            chunk.map { node ->
                val ping = testLatency(node)
                onProgress(node.id, ping)
            }
        }
    }
}
