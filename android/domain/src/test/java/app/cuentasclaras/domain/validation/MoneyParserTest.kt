package app.cuentasclaras.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {

    @Test
    fun `acepta el punto y la coma como separador decimal`() {
        assertEquals(1250L, MoneyParser.parseMinor("12.50"))
        assertEquals(1250L, MoneyParser.parseMinor("12,50"))
        assertEquals(1250L, MoneyParser.parseMinor("  12,50  "))
    }

    @Test
    fun `sin decimales se interpreta como unidades enteras`() {
        assertEquals(1200L, MoneyParser.parseMinor("12"))
        assertEquals(0L, MoneyParser.parseMinor("0"))
    }

    @Test
    fun `el separador de millares no rompe la lectura`() {
        assertEquals(100050L, MoneyParser.parseMinor("1 000,50"))
        assertEquals(100050L, MoneyParser.parseMinor("1000,50"))
    }

    @Test
    fun `rechaza importes invalidos o con mas de dos decimales`() {
        assertNull(MoneyParser.parseMinor(""))
        assertNull(MoneyParser.parseMinor("12.345"))
        assertNull(MoneyParser.parseMinor("-1"))
        assertNull(MoneyParser.parseMinor("abc"))
        assertNull(MoneyParser.parseMinor("12,50,"))
    }

    @Test
    fun `convierte unidades minimas en texto con dos decimales`() {
        assertEquals("12.50", MoneyParser.toPlainString(1250))
        assertEquals("0.00", MoneyParser.toPlainString(0))
        assertEquals("1000.05", MoneyParser.toPlainString(100005))
    }

    @Test
    fun `ida y vuelta de un importe`() {
        val minor = MoneyParser.parseMinor("9999,99")
        assertEquals(999999L, minor)
        assertEquals("9999.99", MoneyParser.toPlainString(minor!!))
    }

    @Test
    fun `porcentajes y pesos se leen como decimales exactos`() {
        assertEquals(java.math.BigDecimal("33.33"), MoneyParser.parseNumber("33,33"))
        assertEquals(java.math.BigDecimal("100"), MoneyParser.parseNumber("100"))
        assertEquals(java.math.BigDecimal("33.3333333333"), MoneyParser.parseNumber("33.3333333333"))
    }

    @Test
    fun `rechaza numeros invalidos`() {
        assertNull(MoneyParser.parseNumber(""))
        assertNull(MoneyParser.parseNumber("-1"))
        assertNull(MoneyParser.parseNumber("1,5,"))
        assertNull(MoneyParser.parseNumber("uno"))
    }
}
