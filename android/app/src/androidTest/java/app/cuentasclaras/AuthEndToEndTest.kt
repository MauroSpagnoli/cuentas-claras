package app.cuentasclaras

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.result.AppResult
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verificación end-to-end real: usa el grafo Hilt completo de la app (Retrofit,
 * interceptores, almacenamiento cifrado y Room) contra el backend Spring Boot
 * levantado en el host (`10.0.2.2:8080`).
 *
 * Si el backend no está accesible, el test se marca como omitido en lugar de
 * fallar, de modo que `connectedDebugAndroidTest` es seguro en CI sin backend.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AuthEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var tokenStore: AuthTokenStore

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue("Backend no accesible en $HOST:$PORT", isBackendReachable())
    }

    @Test
    fun register_login_restore_and_logout_against_real_backend() = runBlocking {
        val email = "e2e-${System.currentTimeMillis()}@example.com"
        val password = "secret123"

        // 1) Registro: devuelve sesión y la persiste localmente.
        val registerResult = authRepository.register(email, password, "E2E Tester", "EUR")
        assertTrue("register falló: $registerResult", registerResult is AppResult.Success)
        val registeredUser = (registerResult as AppResult.Success).value.user
        assertEquals(email, registeredUser.email)
        assertEquals("EUR", registeredUser.defaultCurrency)

        // 2) La sesión queda persistida y es recuperable (token store + Room).
        val restored = authRepository.currentSession()
        assertNotNull("la sesión no se restauró", restored)
        assertEquals(email, restored!!.user.email)
        assertNotNull(tokenStore.snapshot()?.refreshToken)

        // 3) Logout limpia el estado local.
        assertTrue(authRepository.logout() is AppResult.Success)
        assertNull(tokenStore.snapshot())
        assertNull(authRepository.currentSession())

        // 4) Login de nuevo con las mismas credenciales.
        val loginResult = authRepository.login(email, password)
        assertTrue("login falló: $loginResult", loginResult is AppResult.Success)
        assertEquals(email, (loginResult as AppResult.Success).value.user.email)

        // 5) Credenciales inválidas -> error de dominio tipado (401).
        val badLogin = authRepository.login(email, "wrong-password")
        assertTrue(badLogin is AppResult.Failure)
        assertTrue(
            "se esperaba Unauthorized, fue ${(badLogin as AppResult.Failure).error}",
            badLogin.error == app.cuentasclaras.domain.error.AppError.Unauthorized,
        )
    }

    private fun isBackendReachable(): Boolean = try {
        val connection = URL("http://$HOST:$PORT/actuator/health")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.responseCode in 200..299
    } catch (throwable: Throwable) {
        false
    }

    private companion object {
        const val HOST = "10.0.2.2"
        const val PORT = 8080
        const val TIMEOUT_MILLIS = 3_000
    }
}
