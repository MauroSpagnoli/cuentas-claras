package app.cuentasclaras.core.network.interceptor

import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.model.StoredTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenRefreshAuthenticatorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val store = FakeTokenStore(StoredTokens("stale", "refresh-1", 0L))
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val authenticator = TokenRefreshAuthenticator(
            tokenStore = store,
            json = json,
            baseUrl = server.url("/api/v1/").toString(),
            refreshClient = OkHttpClient.Builder().build(),
        )
        client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(store))
            .authenticator(authenticator)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refreshes the token on 401 and replays the request`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            jsonResponse("""{"accessToken":"fresh","refreshToken":"refresh-2","expiresIn":3600}"""),
        )
        server.enqueue(jsonResponse("""{"id":"u-1","email":"e@x.com","displayName":"Ana"}"""))

        val response = client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute()

        assertTrue(response.isSuccessful)
        assertEquals("Bearer fresh", response.request.header("Authorization"))
        assertEquals("fresh", store.snapshot()?.accessToken)
        response.close()

        val firstAttempt = server.takeRequest()
        assertEquals("/api/v1/users/me", firstAttempt.path)
        assertEquals("Bearer stale", firstAttempt.getHeader("Authorization"))

        val refreshAttempt = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refreshAttempt.path)
        assertTrue(refreshAttempt.body.readUtf8().contains("refresh-1"))

        val replay = server.takeRequest()
        assertEquals("Bearer fresh", replay.getHeader("Authorization"))
    }

    @Test
    fun `clears the session when the refresh fails`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("""{"title":"Unauthorized","status":401}"""),
        )

        val response = client.newCall(Request.Builder().url(server.url("/api/v1/users/me")).build()).execute()

        assertEquals(401, response.code)
        assertNull(store.snapshot())
        response.close()
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private class FakeTokenStore(initial: StoredTokens?) : AuthTokenStore {
        private val state = MutableStateFlow(initial)

        override fun snapshot(): StoredTokens? = state.value

        override fun save(tokens: StoredTokens) {
            state.value = tokens
        }

        override fun clear() {
            state.value = null
        }

        override fun observe(): Flow<StoredTokens?> = state
    }
}
