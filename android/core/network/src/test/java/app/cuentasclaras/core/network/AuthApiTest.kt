package app.cuentasclaras.core.network

import app.cuentasclaras.core.network.api.AuthApi
import app.cuentasclaras.core.network.dto.LoginRequestDto
import app.cuentasclaras.core.network.dto.RegisterRequestDto
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.result.AppResult
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class AuthApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts credentials and parses tokens`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "accessToken": "access-1",
                  "refreshToken": "refresh-1",
                  "tokenType": "Bearer",
                  "expiresIn": 3600,
                  "user": {
                    "id": "u-1",
                    "email": "user@example.com",
                    "displayName": "Ana",
                    "defaultCurrency": "EUR"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = safeApiCall(json) { api.login(LoginRequestDto("user@example.com", "secret123")) }

        assertTrue(result is AppResult.Success)
        val body = (result as AppResult.Success).value
        assertEquals("access-1", body.accessToken)
        assertEquals("u-1", body.user?.id)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v1/auth/login", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"email\":\"user@example.com\""))
    }

    @Test
    fun `unauthorized response maps to Unauthorized error`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"title":"Unauthorized","status":401,"detail":"Credenciales inválidas"}""",
                code = 401,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) { api.login(LoginRequestDto("user@example.com", "wrong")) }

        assertEquals(AppError.Unauthorized, (result as AppResult.Failure).error)
    }

    @Test
    fun `validation response exposes field errors`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Datos inválidos",
                  "errors": [{"field": "email", "message": "formato incorrecto"}]
                }
                """.trimIndent(),
                code = 400,
                contentType = "application/problem+json",
            ),
        )

        val result = safeApiCall(json) {
            api.register(RegisterRequestDto(email = "bad", password = "secret123", displayName = "Ana"))
        }

        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Validation)
        assertEquals("formato incorrecto", (error as AppError.Validation).fieldErrors["email"])
    }

    @Test
    fun `server error maps to Server error with status`() = runBlocking {
        server.enqueue(jsonResponse("""{"title":"Boom","status":500}""", code = 500))

        val result = safeApiCall(json) { api.currentUser() }

        assertEquals(AppError.Server(500, "Boom"), (result as AppResult.Failure).error)
    }

    private fun jsonResponse(
        body: String,
        code: Int = 200,
        contentType: String = "application/json",
    ): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", contentType)
        .setBody(body)
}
