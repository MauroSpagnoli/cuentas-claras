package app.cuentasclaras.core.network.interceptor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class IdempotencyInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(IdempotencyInterceptor { "fixed-key" })
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds idempotency key to write requests`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder()
                .url(server.url("/api/v1/groups"))
                .post("{}".toRequestBody(JSON))
                .build(),
        ).execute().close()

        assertEquals("fixed-key", server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `keeps an explicit idempotency key`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(
            Request.Builder()
                .url(server.url("/api/v1/groups"))
                .header(IdempotencyInterceptor.HEADER, "retry-key")
                .post("{}".toRequestBody(JSON))
                .build(),
        ).execute().close()

        assertEquals("retry-key", server.takeRequest().getHeader("Idempotency-Key"))
    }

    @Test
    fun `does not add key to read requests`() {
        server.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).get().build())
            .execute()
            .close()

        assertNull(server.takeRequest().getHeader("Idempotency-Key"))
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
