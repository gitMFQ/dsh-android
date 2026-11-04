package uk.xa0.dsh.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a Translate action sends, and what it accepts back.
 *
 * Both halves are pure so they can be pinned here rather than on a device: the target
 * language decides whether the answer is even the language the reader reads, and the
 * reply shape decides whether a working translation is shown or silently dropped.
 */
class TranslationTargetTest {

    @Test
    fun `a plain language tag passes through`() {
        assertEquals("en", translationTargetOf("en"))
        assertEquals("ja", translationTargetOf("ja"))
        assertEquals("zh", translationTargetOf("zh"))
    }

    /**
     * A regioned tag keeps only its language: the host normalizes `zh-CN` and friends
     * itself, and every other locale has no region-specific translation to ask for.
     */
    @Test
    fun `a regioned or scripted tag is reduced to its language`() {
        assertEquals("zh", translationTargetOf("zh-CN"))
        assertEquals("zh", translationTargetOf("zh-Hant-TW"))
        assertEquals("pt", translationTargetOf("pt_BR"))
        assertEquals("en", translationTargetOf("en-US"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals("fr", translationTargetOf("FR"))
        assertEquals("fr", translationTargetOf("Fr-ca"))
    }

    /**
     * Nothing to derive from is English rather than a guess: a button whose target was
     * invented is worse than one that plainly says what it asked for.
     */
    @Test
    fun `an empty or unusable tag falls back to English`() {
        assertEquals("en", translationTargetOf(null))
        assertEquals("en", translationTargetOf(""))
        assertEquals("en", translationTargetOf("   "))
        assertEquals("en", translationTargetOf("x"))
        assertEquals("en", translationTargetOf("123"))
    }

    // ------------------------------------------------------------- the reply

    @Test
    fun `a plain string reply is the translation`() {
        assertEquals("Bonjour", translationTextOf("Bonjour"))
    }

    @Test
    fun `an object reply is read for its text`() {
        assertEquals("Bonjour", translationTextOf(JSONObject().put("text", "Bonjour")))
        assertEquals("Bonjour", translationTextOf(JSONObject().put("translation", "Bonjour")))
    }

    /**
     * An empty translation is not a translation: showing a blank card would be a worse
     * answer than showing nothing and leaving the button pressable.
     */
    @Test
    fun `a blank or unexpected reply is nothing at all`() {
        assertNull(translationTextOf(""))
        assertNull(translationTextOf("   "))
        assertNull(translationTextOf(JSONObject()))
        assertNull(translationTextOf(JSONObject().put("text", "")))
        assertNull(translationTextOf(null))
        assertNull(translationTextOf(42))
    }
}