package com.cangshuo.toolbox.feature.sync.data

import com.cangshuo.toolbox.feature.sync.domain.*
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RemoteSyncTransportTest {
    @Test fun realRetrofitRoundTripReadsMixedTypedRecordsAndBoundCursor() = runTest {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val cursor=Base64.getUrlEncoder().withoutPadding().encodeToString("1:2".toByteArray())
        val now=Instant.now().toEpochMilli()
        var seenPush=false
        server.createContext("/api/v1/sync/push") { exchange ->
            val body=exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            seenPush=body.contains("calculator") && exchange.requestHeaders.getFirst("Authorization")=="Bearer test"
            val response="""{"code":0,"message":"success","traceId":"trace","data":{"applied":1,"nextCursor":"$cursor"}}"""
            exchange.responseHeaders.set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.createContext("/api/v1/sync/pull") { exchange ->
            val response="""{"code":0,"message":"success","traceId":"trace","data":{"items":[{"entityType":"FAVORITE","entityKey":"calculator","updatedAt":$now,"deviceId":"${"a".repeat(32)}","deleted":false,"payload":{},"revision":1},{"entityType":"SETTING","entityKey":"theme","updatedAt":$now,"deviceId":"${"a".repeat(32)}","deleted":false,"payload":{"value":"DARK"},"revision":2}],"nextCursor":"$cursor","hasMore":false}}"""
            exchange.responseHeaders.set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val transport=RemoteSyncTransport("http://127.0.0.1:${server.address.port}/api/v1") { user,_ ->
                require(user>0);"Bearer test"
            }
            transport.push(1,"a".repeat(32),listOf(SyncRecord("FAVORITE","calculator",now,"a".repeat(32),false)))
            assertTrue(seenPush)
            val page=transport.pull(1,"")
            assertEquals(2,page.items.size);assertEquals("DARK",page.items[1].value);assertEquals(cursor,page.nextCursor)
            try { transport.pull(2,"");fail() } catch(_: IllegalArgumentException) { }
        } finally { server.stop(0) }
    }
}
