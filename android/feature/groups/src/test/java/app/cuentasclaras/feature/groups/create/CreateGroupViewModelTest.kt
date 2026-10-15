package app.cuentasclaras.feature.groups.create

import app.cuentasclaras.domain.error.AppError
import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.result.AppResult
import app.cuentasclaras.domain.usecase.groups.CreateGroupUseCase
import app.cuentasclaras.domain.validation.GroupField
import app.cuentasclaras.domain.validation.ValidationIssue
import app.cuentasclaras.feature.groups.MainDispatcherRule
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

class CreateGroupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val createGroup = mockk<CreateGroupUseCase>()

    private fun createViewModel() = CreateGroupViewModel(createGroup)

    @Test
    fun `cannot submit while the name is blank`() {
        val viewModel = createViewModel()

        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onEvent(CreateGroupEvent.NameChanged("  "))

        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `blank name surfaces a required issue and skips the use case`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.onEvent(CreateGroupEvent.Submitted)
            advanceUntilIdle()

            assertEquals(ValidationIssue.Required, viewModel.state.value.fieldIssues[GroupField.NAME])
            coVerify(exactly = 0) { createGroup(any(), any(), any()) }
        }

    @Test
    fun `invalid currency surfaces the currency issue`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(CreateGroupEvent.NameChanged("Piso"))
        viewModel.onEvent(CreateGroupEvent.CurrencyChanged("EU"))
        viewModel.onEvent(CreateGroupEvent.Submitted)
        advanceUntilIdle()

        assertEquals(ValidationIssue.InvalidCurrency, viewModel.state.value.fieldIssues[GroupField.CURRENCY])
        coVerify(exactly = 0) { createGroup(any(), any(), any()) }
    }

    @Test
    fun `valid form creates the group and exposes its id`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { createGroup("Piso", "Alquiler", "eur") } returns AppResult.Success(
            Group(id = "g-1", name = "Piso", currency = "EUR"),
        )
        val viewModel = createViewModel()

        viewModel.onEvent(CreateGroupEvent.NameChanged("Piso"))
        viewModel.onEvent(CreateGroupEvent.DescriptionChanged("Alquiler"))
        viewModel.onEvent(CreateGroupEvent.CurrencyChanged("eur"))
        viewModel.onEvent(CreateGroupEvent.Submitted)
        advanceUntilIdle()

        assertEquals("g-1", viewModel.state.value.createdGroupId)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `network failure surfaces the error and stops submitting`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { createGroup(any(), any(), any()) } returns AppResult.Failure(AppError.Network)
            val viewModel = createViewModel()

            viewModel.onEvent(CreateGroupEvent.NameChanged("Piso"))
            viewModel.onEvent(CreateGroupEvent.Submitted)
            advanceUntilIdle()

            assertEquals(AppError.Network, viewModel.state.value.error)
            assertFalse(viewModel.state.value.isSubmitting)
            assertNull(viewModel.state.value.createdGroupId)
        }

    @Test
    fun `handling the created group clears the navigation signal`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { createGroup(any(), any(), any()) } returns AppResult.Success(
                Group(id = "g-1", name = "Piso"),
            )
            val viewModel = createViewModel()

            viewModel.onEvent(CreateGroupEvent.NameChanged("Piso"))
            viewModel.onEvent(CreateGroupEvent.Submitted)
            advanceUntilIdle()
            viewModel.onEvent(CreateGroupEvent.CreatedGroupHandled)

            assertNull(viewModel.state.value.createdGroupId)
        }

    @Test
    fun `editing the name clears its field issue`() {
        val viewModel = createViewModel()

        viewModel.onEvent(CreateGroupEvent.Submitted)
        assertTrue(viewModel.state.value.fieldIssues.containsKey(GroupField.NAME))

        viewModel.onEvent(CreateGroupEvent.NameChanged("Piso"))

        assertFalse(viewModel.state.value.fieldIssues.containsKey(GroupField.NAME))
    }
}
