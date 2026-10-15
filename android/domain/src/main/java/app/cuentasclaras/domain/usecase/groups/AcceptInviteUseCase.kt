package app.cuentasclaras.domain.usecase.groups

import app.cuentasclaras.domain.model.Group
import app.cuentasclaras.domain.repository.GroupRepository
import app.cuentasclaras.domain.result.AppResult
import javax.inject.Inject

/**
 * Acepta una invitación. Acepta tanto el código corto como la URL completa
 * (`https://cuentas-claras.app/invite/ABCDEFGHIJ`), de la que extrae el código.
 */
class AcceptInviteUseCase @Inject constructor(
    private val repository: GroupRepository,
) {
    suspend operator fun invoke(rawCode: String): AppResult<Group> =
        repository.acceptInvite(normalize(rawCode))

    private fun normalize(rawCode: String): String {
        val trimmed = rawCode.trim()
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
        val code = withoutQuery.substringAfterLast('/').ifEmpty { withoutQuery }
        return code.trim().uppercase()
    }
}
