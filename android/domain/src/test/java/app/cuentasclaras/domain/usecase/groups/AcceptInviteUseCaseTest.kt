package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AcceptInviteUseCaseTest {

    private val repository: GroupRepository = mockk(relaxed = true)
    private val useCase = AcceptInviteUseCase(repository)

    private fun stubAccept(expected: String) {
        coEvery { repository.acceptInvite(expected) } returns AppResult.Success(
            Group(id = "g1", name = "Piso"),
        )
    }

    @Test
    fun `normaliza el codigo a mayusculas`() = runTest {
        stubAccept("ABCDEFGHIJ")

        val result = useCase(" abcdefghij ")

        assertEquals("g1", (result as AppResult.Success).value.id)
        coVerify(exactly = 1) { repository.acceptInvite("ABCDEFGHIJ") }
    }

    @Test
    fun `extrae el codigo de una url de invitacion`() = runTest {
        stubAccept("ABCDEFGHIJ")

        useCase("https://cuentas-claras.app/invite/abcdefghij")

        coVerify(exactly = 1) { repository.acceptInvite("ABCDEFGHIJ") }
    }

    @Test
    fun `ignora parametros de consulta y barra final`() = runTest {
        stubAccept("ABCDEFGHIJ")

        useCase("https://cuentas-claras.app/invite/abcdefghij/?utm_source=whatsapp")

        coVerify(exactly = 1) { repository.acceptInvite("ABCDEFGHIJ") }
    }

    @Test
    fun `propaga el fallo del repositorio`() = runTest {
        coEvery { repository.acceptInvite(any()) } returns AppResult.Failure(
            app.cuentasclaras.domain.error.AppError.NotFound,
        )

        useCase("ABCDEFGHIJ") as AppResult.Failure

        coVerify(exactly = 1) { repository.acceptInvite("ABCDEFGHIJ") }
    }
}
