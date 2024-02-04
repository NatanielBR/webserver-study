import natanielbr.study.webserver.utils.RegexBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestRegexBuilder {

    @Test
    fun testMatchText() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchText("test")
        assertTrue(regexBuilder.validate("test"))
    }

    @Test
    fun testMatchAnyWord() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyText()
        assertTrue(regexBuilder.validate("test"))
    }

    @Test
    fun testMatchAnyWordSequence() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyText(true)
        assertTrue(regexBuilder.validate("test"))
    }

    @Test
    fun testMatchAnyNumber() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyNumber()
        assertTrue(regexBuilder.validate("123"))
    }

    @Test
    fun testUnMatchAnyNumber() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyNumber()
        assertFalse(regexBuilder.validate("abc"))
    }

    @Test
    fun testMatchAnyNumberSequence() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyNumber(true)
        assertTrue(regexBuilder.validate("123"))
    }

    @Test
    fun testUnMatchAnyNumberSequence() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchAnyNumber(true)
        assertFalse(regexBuilder.validate("abc"))
    }

    @Test
    fun testMatchBoolean() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchBoolean()
        assertTrue(regexBuilder.validate("true"))
    }

    @Test
    fun testUnMatchBoolean() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchBoolean()
        assertFalse(regexBuilder.validate("abc"))
    }

    @Test
    fun testMatchFloatNumber() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchFloatNumber()
        assertTrue(regexBuilder.validate("123.123"))
    }

    @Test
    fun testUnMatchFloatNumber() {
        val regexBuilder = RegexBuilder()
        regexBuilder.matchFloatNumber()
        assertFalse(regexBuilder.validate("abc"))
    }
}