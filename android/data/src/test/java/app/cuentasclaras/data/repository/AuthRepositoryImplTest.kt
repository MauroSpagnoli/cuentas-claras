package app.cuentasclaras.data.repository

import app.cuentasclaras.core.database.dao.UserDao
import app.cuentasclaras.core.database.entity.UserEntity
import app.cuentasclaras.core.network.api.AuthApi
import app.cuentasclaras.core.network.dto.AuthTokensDto
import app.cuentasclaras.core.network.dto.UserDto
import app.cuentasclaras.domain.auth.AuthTokenStore
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.StoredTokens
import app.cuentasclaras.domain.result.AppResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class AuthRepositoryImplTest {

    private val api = mockk<AuthApi>()
    private val userDao = mockk<UserDao>()
    private val tokenStore = InMemoryTokenStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val repository = AuthRepositoryImpl(
        authApi = api,
        tokenStore = tokenStore,
        userDao = userDao,
        json = json,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `login persists tokens and current user`() = runTest {
        coEvery { api.login(any()) } returns Response.success(authTokensWithUser())
        coEvery { userDao.clear() } just Runs
        coEvery { userDao.upsert(any()) } just Runs

        val result = repository.login("user@example.com", "secret123")

        assertTrue(result is AppResult.Success)
        assertEquals("access-1", tokenStore.snapshot()?.accessToken)
        assertEquals("Ana", (result as AppResult.Success).value.user.displayName)

        val capturedUser = slot<UserEntity>()
        coVerify { userDao.upsert(capture(capturedUser)) }
        assertEquals("u-1", capturedUser.captured.id)
    }

    @Test
    fun `login discards the previous current user`() = runTest {
        coEvery { api.login(any()) } returns Response.success(authTokensWithUser())
        coEvery { userDao.clear() } just Runs
        coEvery { userDao.upsert(any()) } just Runs

        repository.login("user@example.com", "secret123")

        // `current_user` es de una sola fila: el login anterior se descarta.
        coVerify(exactly = 1) { userDao.clear() }
    }

    @Test
    fun `login failure persists nothing`() = runTest {
        coEvery { api.login(any()) } throws IOException("offline")

        val result = repository.login("user@example.com", "bad")

        assertEquals(AppError.Network, (result as AppResult.Failure).error)
        assertNull(tokenStore.snapshot())
        coVerify(exactly = 0) { userDao.upsert(any()) }
    }

    @Test
    fun `authenticate fails when the response has no user`() = runTest {
        coEvery { api.login(any()) } returns Response.success(AuthTokensDto("a", "r", expiresIn = 3600, user = null))

        val result = repository.login("user@example.com", "pw")

        assertTrue(result is AppResult.Failure)
        assertNull(tokenStore.snapshot())
    }

    @Test
    fun `logout clears local state even when the network fails`() = runTest {
        tokenStore.save(StoredTokens("a", "r", 0L))
        coEvery { api.logout(any()) } throws IOException("offline")
        coEvery { userDao.clear() } just Runs

        val result = repository.logout()

        assertTrue(result is AppResult.Success)
        assertNull(tokenStore.snapshot())
        coVerify { userDao.clear() }
    }

    private fun authTokens(user: UserDto?) = AuthTokensDto(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresIn = 3600,
        user = user,
    )

    private fun authTokensWithUser() = authTokens(
        UserDto(id = "u-1", email = "e@x.com", displayName = "Ana", defaultCurrency = "EUR"),
    )

    private class InMemoryTokenStore : AuthTokenStore {
        private val state = MutableStateFlow<StoredTokens?>(null)

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
