package app.cuentasclaras.feature.auth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.AuthSession
import app.cuentasclaras.domain.model.StoredTokens
import app.cuentasclaras.domain.model.User
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.auth.LoginUseCase
import app.cuentasclaras.domain.usecase.auth.RegisterUseCase
import app.cuentasclaras.domain.validation.AuthField
import app.cuentasclaras.domain.validation.ValidationIssue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val login = mockk<LoginUseCase>()
    private val register = mockk<RegisterUseCase>()

    @Test
    fun `defaults to login mode without argument`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        assertEquals(AuthMode.LOGIN, viewModel.state.value.mode)
        assertFalse(viewModel.state.value.isRegister)
    }

    @Test
    fun `reads register mode from navigation argument`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(AuthRoutes.MODE_REGISTER)

        assertEquals(AuthMode.REGISTER, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.isRegister)
    }

    @Test
    fun `invalid email surfaces field issue and skips the use case`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(AuthEvent.EmailChanged("not-an-email"))
        viewModel.onEvent(AuthEvent.PasswordChanged("secret123"))
        viewModel.onEvent(AuthEvent.Submit)

        assertEquals(ValidationIssue.InvalidEmail, viewModel.state.value.fieldIssues[AuthField.EMAIL])
        assertFalse(viewModel.state.value.isSubmitting)
        coVerify(exactly = 0) { login(any(), any()) }
    }

    @Test
    fun `successful login emits loading then settles without error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { login(any(), any()) } returns AppResult.Success(session())

        val viewModel = createViewModel()
        viewModel.onEvent(AuthEvent.EmailChanged("user@example.com"))
        viewModel.onEvent(AuthEvent.PasswordChanged("secret123"))

        viewModel.state.test {
            assertFalse(awaitItem().isSubmitting)

            viewModel.onEvent(AuthEvent.Submit)

            assertTrue(awaitItem().isSubmitting)
            val settled = awaitItem()
            assertFalse(settled.isSubmitting)
            assertNull(settled.error)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { login("user@example.com", "secret123") }
    }

    @Test
    fun `failed register exposes the domain error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { register(any(), any(), any(), any()) } returns AppResult.Failure(AppError.Conflict(null))

        val viewModel = createViewModel(AuthRoutes.MODE_REGISTER)
        viewModel.onEvent(AuthEvent.EmailChanged("user@example.com"))
        viewModel.onEvent(AuthEvent.PasswordChanged("secret123"))
        viewModel.onEvent(AuthEvent.DisplayNameChanged("Ana"))

        viewModel.state.test {
            awaitItem()

            viewModel.onEvent(AuthEvent.Submit)
            assertTrue(awaitItem().isSubmitting)

            val settled = awaitItem()
            assertFalse(settled.isSubmitting)
            assertEquals(AppError.Conflict(null), settled.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing clears a previous error`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { login(any(), any()) } returns AppResult.Failure(AppError.Network)

        val viewModel = createViewModel()
        viewModel.onEvent(AuthEvent.EmailChanged("user@example.com"))
        viewModel.onEvent(AuthEvent.PasswordChanged("secret123"))

        viewModel.onEvent(AuthEvent.Submit)
        advanceUntilIdle()
        assertEquals(AppError.Network, viewModel.state.value.error)

        viewModel.onEvent(AuthEvent.EmailChanged("other@example.com"))
        assertNull(viewModel.state.value.error)
    }

    private fun createViewModel(mode: String = AuthRoutes.MODE_LOGIN): AuthViewModel =
        AuthViewModel(
            login = login,
            register = register,
            savedStateHandle = SavedStateHandle(mapOf(AuthRoutes.ARG_MODE to mode)),
        )

    private fun session() = AuthSession(
        user = User(
            id = "u-1",
            email = "user@example.com",
            displayName = "Ana",
            defaultCurrency = "EUR",
            locale = null,
            createdAt = null,
        ),
        tokens = StoredTokens(
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 0L,
        ),
    )
}
