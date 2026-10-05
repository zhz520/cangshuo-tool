package com.cangshuo.toolbox.feature.auth.data

import com.cangshuo.toolbox.feature.auth.domain.AuthException
import com.cangshuo.toolbox.feature.auth.domain.AuthFailure
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RemoteAuthRepositoryTest {
    @Test fun realHttpLoginConfirmsMeThenPublishesAndLogsOut() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var meCalls = 0
        server.createContext("/api/v1/auth/login") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertTrue(exchange.requestBody.bufferedReader().use { it.readText() }.contains("a@b.com"))
            exchange.respond(200, """{"code":0,"message":"success","traceId":"trace","data":{"accessToken":"${"a".repeat(40)}","tokenType":"Bearer","refreshToken":"${"12".repeat(16)}.${"a".repeat(43)}","refreshExpiresAt":"${Instant.now().plusSeconds(2592000)}","expiresIn":900,"expiresAt":"${Instant.now().plusSeconds(900)}","user":{"id":1,"email":"a@b.com","nickname":"用户"}}}""")
        }
        server.createContext("/api/v1/auth/me") { exchange ->
            meCalls++
            assertEquals("Bearer ${"a".repeat(40)}", exchange.requestHeaders.getFirst("Authorization"))
            exchange.respond(200, """{"code":0,"message":"success","traceId":"trace","data":{"id":1,"email":"a@b.com","nickname":"用户"}}""")
        }
        server.createContext("/api/v1/auth/logout") { exchange -> exchange.respond(200, """{"code":0,"message":"success","traceId":"trace","data":null}""") }
        server.start()
        try {
            val repository = RemoteAuthRepository("http://127.0.0.1:${server.address.port}/api/v1", backgroundScope)
            repository.login("a@b.com", "password12")
            assertEquals(1, meCalls)
            assertEquals("用户", repository.account.value?.nickname)
            repository.logout()
            assertNull(repository.account.value)
        } finally { server.stop(0) }
    }

    @Test fun httpFailureAndOversizedChunkedResponseNeverPublishAccount() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var oversized = false
        server.createContext("/api/v1/auth/login") { exchange ->
            exchange.requestBody.close()
            if (oversized) {
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { it.write("x".repeat(17_000).toByteArray()) }
            } else exchange.respond(401, """{"code":20001,"message":"error","traceId":"trace","data":null}""")
        }
        server.start()
        try {
            val repository = RemoteAuthRepository("http://127.0.0.1:${server.address.port}/api/v1", backgroundScope)
            try { repository.login("a@b.com", "password12"); fail() }
            catch (exception: AuthException) { assertEquals(AuthFailure.CREDENTIALS, exception.failure) }
            oversized = true
            try { repository.login("a@b.com", "password12"); fail() }
            catch (exception: AuthException) { assertEquals(AuthFailure.NETWORK, exception.failure) }
            assertNull(repository.account.value)
        } finally { server.stop(0) }
    }

    @Test fun expiredLoginResponseCannotPublishAccount() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/auth/login") { exchange ->
            exchange.requestBody.close()
            exchange.respond(200, """{"code":0,"message":"success","traceId":"trace","data":{"accessToken":"${"a".repeat(40)}","tokenType":"Bearer","refreshToken":"${"12".repeat(16)}.${"a".repeat(43)}","refreshExpiresAt":"${Instant.now().plusSeconds(2592000)}","expiresIn":900,"expiresAt":"2020-01-01T00:00:00Z","user":{"id":1,"email":"a@b.com","nickname":"u"}}}""")
        }
        server.start()
        try {
            val repository = RemoteAuthRepository("http://127.0.0.1:${server.address.port}/api/v1", backgroundScope)
            try { repository.login("a@b.com", "password12"); fail() }
            catch (exception: AuthException) { assertEquals(AuthFailure.SERVICE, exception.failure) }
            assertNull(repository.account.value)
        } finally { server.stop(0) }
    }

    private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
