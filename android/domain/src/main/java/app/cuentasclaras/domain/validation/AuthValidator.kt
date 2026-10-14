package app.cuentasclaras.domain.validation

/** Campos validables del formulario de autenticación. */
enum class AuthField { EMAIL, PASSWORD, DISPLAY_NAME, DEFAULT_CURRENCY }

/** Incidencia de validación, independiente del idioma (la UI la traduce). */
sealed interface ValidationIssue {
    data object Required : ValidationIssue
    data object InvalidEmail : ValidationIssue
    data class TooShort(val min: Int) : ValidationIssue
    data class TooLong(val max: Int) : ValidationIssue
    data object InvalidCurrency : ValidationIssue
}

/**
 * Validación pura del formulario de login/registro. Devuelve un mapa
 * campo → incidencia; vacío cuando la entrada es válida.
 */
object AuthValidator {

    const val PASSWORD_MIN_LENGTH = 8
    const val DISPLAY_NAME_MAX_LENGTH = 80

    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val CURRENCY_REGEX = Regex("^[A-Z]{3}$")

    fun validateLogin(email: String, password: String): Map<AuthField, ValidationIssue> {
        val issues = linkedMapOf<AuthField, ValidationIssue>()
        emailIssue(email)?.let { issues[AuthField.EMAIL] = it }
        if (password.isBlank()) issues[AuthField.PASSWORD] = ValidationIssue.Required
        return issues
    }

    fun validateRegister(
        email: String,
        password: String,
        displayName: String,
        defaultCurrency: String,
    ): Map<AuthField, ValidationIssue> {
        val issues = linkedMapOf<AuthField, ValidationIssue>()
        emailIssue(email)?.let { issues[AuthField.EMAIL] = it }
        passwordIssue(password, enforceMinLength = true)?.let { issues[AuthField.PASSWORD] = it }
        displayNameIssue(displayName)?.let { issues[AuthField.DISPLAY_NAME] = it }
        currencyIssue(defaultCurrency)?.let { issues[AuthField.DEFAULT_CURRENCY] = it }
        return issues
    }

    private fun emailIssue(rawEmail: String): ValidationIssue? {
        val email = rawEmail.trim()
        return when {
            email.isEmpty() -> ValidationIssue.Required
            !EMAIL_REGEX.matches(email) -> ValidationIssue.InvalidEmail
            else -> null
        }
    }

    private fun passwordIssue(password: String, enforceMinLength: Boolean): ValidationIssue? = when {
        password.isEmpty() -> ValidationIssue.Required
        enforceMinLength && password.length < PASSWORD_MIN_LENGTH ->
            ValidationIssue.TooShort(PASSWORD_MIN_LENGTH)
        else -> null
    }

    private fun displayNameIssue(rawName: String): ValidationIssue? {
        val name = rawName.trim()
        return when {
            name.isEmpty() -> ValidationIssue.Required
            name.length > DISPLAY_NAME_MAX_LENGTH -> ValidationIssue.TooLong(DISPLAY_NAME_MAX_LENGTH)
            else -> null
        }
    }

    private fun currencyIssue(rawCurrency: String): ValidationIssue? =
        if (CURRENCY_REGEX.matches(rawCurrency.trim().uppercase())) null else ValidationIssue.InvalidCurrency
}
