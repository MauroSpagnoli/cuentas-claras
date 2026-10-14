package app.cuentasclaras.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun `login requires email and password`() {
        val issues = AuthValidator.validateLogin(email = "  ", password = "")

        assertEquals(ValidationIssue.Required, issues[AuthField.EMAIL])
        assertEquals(ValidationIssue.Required, issues[AuthField.PASSWORD])
    }

    @Test
    fun `login rejects malformed email`() {
        val issues = AuthValidator.validateLogin(email = "user@localhost", password = "secret123")

        assertEquals(ValidationIssue.InvalidEmail, issues[AuthField.EMAIL])
        assertFalse(issues.containsKey(AuthField.PASSWORD))
    }

    @Test
    fun `login accepts valid credentials`() {
        val issues = AuthValidator.validateLogin(email = "user@example.com", password = "any")

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `register enforces minimum password length`() {
        val issues = AuthValidator.validateRegister(
            email = "user@example.com",
            password = "short",
            displayName = "Ana",
            defaultCurrency = "EUR",
        )

        assertEquals(
            ValidationIssue.TooShort(AuthValidator.PASSWORD_MIN_LENGTH),
            issues[AuthField.PASSWORD],
        )
    }

    @Test
    fun `register rejects invalid currency`() {
        val issues = AuthValidator.validateRegister(
            email = "user@example.com",
            password = "secret123",
            displayName = "Ana",
            defaultCurrency = "euros",
        )

        assertEquals(ValidationIssue.InvalidCurrency, issues[AuthField.DEFAULT_CURRENCY])
    }

    @Test
    fun `register accepts valid input and normalises currency`() {
        val issues = AuthValidator.validateRegister(
            email = "user@example.com",
            password = "secret123",
            displayName = "  Ana  ",
            defaultCurrency = "eur",
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `register requires display name`() {
        val issues = AuthValidator.validateRegister(
            email = "user@example.com",
            password = "secret123",
            displayName = "   ",
            defaultCurrency = "EUR",
        )

        assertEquals(ValidationIssue.Required, issues[AuthField.DISPLAY_NAME])
    }
}
