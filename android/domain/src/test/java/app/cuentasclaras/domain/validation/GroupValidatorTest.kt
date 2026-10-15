package app.cuentasclaras.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupValidatorTest {

    @Test
    fun `nombre vacio es obligatorio`() {
        assertEquals(
            ValidationIssue.Required,
            GroupValidator.validateName("   "),
        )
    }

    @Test
    fun `nombre demasiado largo`() {
        val tooLong = "a".repeat(GroupValidator.NAME_MAX_LENGTH + 1)
        assertEquals(
            ValidationIssue.TooLong(GroupValidator.NAME_MAX_LENGTH),
            GroupValidator.validateName(tooLong),
        )
    }

    @Test
    fun `nombre valido en el limite`() {
        assertNull(GroupValidator.validateName("a".repeat(GroupValidator.NAME_MAX_LENGTH)))
        assertNull(GroupValidator.validateName("  Viaje a Lisboa  "))
    }

    @Test
    fun `descripcion vacia es valida`() {
        assertNull(GroupValidator.validateDescription(""))
        assertNull(GroupValidator.validateDescription("   "))
    }

    @Test
    fun `descripcion demasiado larga`() {
        val tooLong = "b".repeat(GroupValidator.DESCRIPTION_MAX_LENGTH + 1)
        assertEquals(
            ValidationIssue.TooLong(GroupValidator.DESCRIPTION_MAX_LENGTH),
            GroupValidator.validateDescription(tooLong),
        )
    }

    @Test
    fun `moneda debe ser iso de tres letras`() {
        assertNull(GroupValidator.validateCurrency("eur"))
        assertNull(GroupValidator.validateCurrency(" EUR "))
        assertEquals(ValidationIssue.InvalidCurrency, GroupValidator.validateCurrency("EU"))
        assertEquals(ValidationIssue.InvalidCurrency, GroupValidator.validateCurrency("EURO"))
        assertEquals(ValidationIssue.InvalidCurrency, GroupValidator.validateCurrency(""))
    }

    @Test
    fun `email de miembro`() {
        assertNull(GroupValidator.validateEmail("ana@example.com"))
        assertEquals(ValidationIssue.Required, GroupValidator.validateEmail("  "))
        assertEquals(ValidationIssue.InvalidEmail, GroupValidator.validateEmail("ana@"))
    }

    @Test
    fun `validateCreateGroup acumula incidencias por campo`() {
        val issues = GroupValidator.validateCreateGroup(name = "", description = "", currency = "x")

        assertEquals(ValidationIssue.Required, issues[GroupField.NAME])
        assertEquals(ValidationIssue.InvalidCurrency, issues[GroupField.CURRENCY])
        assertTrue(GroupField.DESCRIPTION !in issues)
    }

    @Test
    fun `validateCreateGroup sin incidencias`() {
        val issues = GroupValidator.validateCreateGroup(
            name = "Piso",
            description = "Gastos comunes",
            currency = "EUR",
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `validateAddMember apunta al campo email`() {
        val issues = GroupValidator.validateAddMember("no-es-un-email")
        assertEquals(ValidationIssue.InvalidEmail, issues[MemberField.EMAIL])
    }
}
