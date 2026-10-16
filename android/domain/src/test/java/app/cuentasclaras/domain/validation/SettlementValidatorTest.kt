package app.cuentasclaras.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementValidatorTest {

    @Test
    fun `a complete payment has no issues`() {
        val issues = SettlementValidator.validate(
            fromUserId = "u-1",
            toUserId = "u-2",
            amountMinor = 1250,
            settledOn = "2026-02-01",
            note = "Bizum",
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `the note is optional`() {
        val issues = SettlementValidator.validate("u-1", "u-2", 1250, "2026-02-01", "")

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `the two parties are required`() {
        val issues = SettlementValidator.validate("", "u-2", 1250, "2026-02-01", "")

        assertEquals(SettlementIssue.Required, issues[SettlementField.PARTIES])
    }

    @Test
    fun `the payer and the payee must differ`() {
        val issues = SettlementValidator.validate("u-1", "u-1", 1250, "2026-02-01", "")

        assertEquals(SettlementIssue.SameParty, issues[SettlementField.PARTIES])
    }

    @Test
    fun `an unparseable amount is invalid`() {
        val issues = SettlementValidator.validate("u-1", "u-2", null, "2026-02-01", "")

        assertEquals(SettlementIssue.InvalidAmount, issues[SettlementField.AMOUNT])
    }

    @Test
    fun `a non-positive amount is rejected`() {
        val issues = SettlementValidator.validate("u-1", "u-2", 0, "2026-02-01", "")

        assertEquals(SettlementIssue.AmountNotPositive, issues[SettlementField.AMOUNT])
    }

    @Test
    fun `an impossible date is rejected`() {
        val issues = SettlementValidator.validate("u-1", "u-2", 1250, "2026-13-01", "")

        assertEquals(SettlementIssue.InvalidDate, issues[SettlementField.DATE])
    }

    @Test
    fun `a free-form date is rejected`() {
        val issues = SettlementValidator.validate("u-1", "u-2", 1250, "01/02/2026", "")

        assertEquals(SettlementIssue.InvalidDate, issues[SettlementField.DATE])
    }

    @Test
    fun `a note over the limit is rejected with its length`() {
        val tooLong = "x".repeat(SettlementValidator.NOTE_MAX_LENGTH + 1)

        val issues = SettlementValidator.validate("u-1", "u-2", 1250, "2026-02-01", tooLong)

        assertEquals(
            SettlementIssue.TooLong(SettlementValidator.NOTE_MAX_LENGTH),
            issues[SettlementField.NOTE],
        )
    }

    @Test
    fun `the whole form is validated at once`() {
        val issues = SettlementValidator.validate("", "", null, "nope", "x".repeat(501))

        assertEquals(
            setOf(
                SettlementField.PARTIES,
                SettlementField.AMOUNT,
                SettlementField.DATE,
                SettlementField.NOTE,
            ),
            issues.keys,
        )
    }

    @Test
    fun `isValidIso accepts real dates and rejects the others`() {
        assertTrue(SettlementValidator.isValidIso("2026-02-01"))
        assertTrue(SettlementValidator.isValidIso("2024-02-29"))
        assertFalse(SettlementValidator.isValidIso("2026-02-30"))
        assertFalse(SettlementValidator.isValidIso("2026-13-01"))
        assertFalse(SettlementValidator.isValidIso("2026-2-1"))
        assertFalse(SettlementValidator.isValidIso(""))
    }
}
