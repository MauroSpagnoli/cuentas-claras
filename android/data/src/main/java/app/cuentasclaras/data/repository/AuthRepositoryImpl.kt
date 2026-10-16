package app.cuentasclaras.data.repository

import app.cuentasclaras.core.common.di.IoDispatcher
import app.cuentasclaras.core.database.dao.UserDao
import app.cuentasclaras.core.network.api.AuthApi
import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.LoginRequestDto
import app.cuentasclaras.core.network.dto.RefreshRequestDto
import app.cuentasclaras.core.network.dto.RegisterRequestDto
import app.cuentasclaras.core.network.safeApiCall
import app.cuentasclaras.data.mapper.toDomain
import app.cuentasclaras.data.mapper.toEntity
import app.cuentasclaras.data.mapper.toStoredTokens
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.repository.AuthRepository
import app.cuentasclaras.domain.result.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: AuthTokenStore,
    private val userDao: app.cuentasclaras.core.database.dao.UserDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override fun observeSession(): Flow<AuthSession?> =
        combine(tokenStore.observe(), userDao.observeCurrent()) { tokens, user ->
            if (tokens != null && user != null) {
                AuthSession(user = user.toDomain(), tokens = tokens)
            } else {
                null
            }
        }.distinctUntilChanged()

    override suspend fun currentSession(): AuthSession? = withContext(ioDispatcher) {
        val tokens = tokenStore.snapshot() ?: return@withContext null
        val user = userDao.current() ?: return@withContext null
        AuthSession(user = user.toDomain(), tokens = tokens)
    }

    override suspend fun login(email: String, password: String): AppResult<AuthSession> =
        authenticate { authApi.login(LoginRequestDto(email = email, password = password)) }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String,
        defaultCurrency: String,
    ): AppResult<AuthSession> = authenticate {
        authApi.register(
            RegisterRequestDto(
                email = email,
                password = password,
                displayName = displayName,
                defaultCurrency = defaultCurrency,
            ),
        )
    }

    override suspend fun logout(): AppResult<Unit> = withContext(ioDispatcher) {
        revokeRefreshToken()
        tokenStore.clear()
        userDao.clear()
        AppResult.Success(Unit)
    }

    private suspend fun authenticate(
        call: suspend () -> Response<AuthTokensDto>,
    ): AppResult<AuthSession> = withContext(ioDispatcher) {
        when (val result = safeApiCall(json, call)) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val user = result.value.user
                if (user == null) {
                    AppResult.Failure(AppError.Unexpected("La respuesta de autenticación no incluye el usuario"))
                } else {
                    val tokens = result.value.toStoredTokens(nowEpochSeconds())
                    tokenStore.save(tokens)
                    // `current_user` es una tabla de una sola fila: al cambiar de
                    // sesión hay que descartar la anterior (si no, `LIMIT 1`
                    // devolvería un usuario antiguo arbitrario).
                    userDao.clear()
                    userDao.upsert(user.toEntity())
                    AppResult.Success(AuthSession(user = user.toDomain(), tokens = tokens))
                }
            }
        }
    }

    private suspend fun revokeRefreshToken() {
        val refreshToken = tokenStore.snapshot()?.refreshToken
        if (refreshToken.isNullOrBlank()) return
        try {
            authApi.logout(RefreshRequestDto(refreshToken))
        } catch (io: IOException) {
            // Best effort: la sesión local se cierra igualmente.
        }
    }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
}
