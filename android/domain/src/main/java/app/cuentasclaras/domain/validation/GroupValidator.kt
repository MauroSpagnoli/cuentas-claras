package app.cuentasclaras.domain.validation

/** Campos validables del formulario de crear grupo. */
enum class GroupField { NAME, DESCRIPTION, CURRENCY }

/** Campos validables del formulario de añadir miembro. */
enum class MemberField { EMAIL }

/**
 * Validación pura de los formularios de grupos, alineada con las restricciones
 * del backend (`CreateGroupRequest`, `AddMemberRequest`).
 */
object GroupValidator {

    const val NAME_MAX_LENGTH = 120
    const val DESCRIPTION_MAX_LENGTH = 500

    private val CURRENCY_REGEX = Regex("^[A-Z]{3}$")
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateCreateGroup(
        name: String,
        description: String,
        currency: String,
    ): Map<GroupField, ValidationIssue> {
        val issues = linkedMapOf<GroupField, ValidationIssue>()
        validateName(name)?.let { issues[GroupField.NAME] = it }
        validateDescription(description)?.let { issues[GroupField.DESCRIPTION] = it }
        validateCurrency(currency)?.let { issues[GroupField.CURRENCY] = it }
        return issues
    }

    fun validateAddMember(email: String): Map<MemberField, ValidationIssue> {
        val issues = linkedMapOf<MemberField, ValidationIssue>()
        validateEmail(email)?.let { issues[MemberField.EMAIL] = it }
        return issues
    }

    fun validateName(rawName: String): ValidationIssue? {
        val name = rawName.trim()
        return when {
            name.isEmpty() -> ValidationIssue.Required
            name.length > NAME_MAX_LENGTH -> ValidationIssue.TooLong(NAME_MAX_LENGTH)
            else -> null
        }
    }

    fun validateDescription(rawDescription: String): ValidationIssue? =
        if (rawDescription.trim().length > DESCRIPTION_MAX_LENGTH) {
            ValidationIssue.TooLong(DESCRIPTION_MAX_LENGTH)
        } else {
            null
        }

    fun validateCurrency(rawCurrency: String): ValidationIssue? =
        if (CURRENCY_REGEX.matches(rawCurrency.trim().uppercase())) null else ValidationIssue.InvalidCurrency

    fun validateEmail(rawEmail: String): ValidationIssue? {
        val email = rawEmail.trim()
        return when {
            email.isEmpty() -> ValidationIssue.Required
            !EMAIL_REGEX.matches(email) -> ValidationIssue.InvalidEmail
            else -> null
        }
    }
}
