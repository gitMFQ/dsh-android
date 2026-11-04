package uk.xa0.dsh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The shell line a save rides, and why it is encoded rather than quoted.
 *
 * There is no `workspaceFiles/write` on the host, so a save goes through the session's
 * own terminal (see `DshViewModel.workspaceFileSaver`). That makes this string a *command
 * line built from user input*, which is the one place in this app where a quoting bug
 * would not fail loudly — it would run something else. So the facts pinned here are the
 * ones that would bite: that the text never appears as itself in the command, that a
 * hostile path cannot escape its quoting, and that a decode failure cannot reach the
 * rename.
 */
class FileWriteCommandTest {

    /** Round-trips the command's own payload, which is what the shell would decode. */
    private fun decoded(command: String): String {
        val start = command.indexOf("'") + 1
        val end = command.indexOf("'", start)
        val base64 = command.substring(start, end)
        return String(Base64.getDecoder().decode(base64), Charsets.UTF_8)
    }

    @Test
    fun `the text is carried as base64 and decodes back exactly`() {
        val text = "fun main() {\n    println(\"hi\")\n}\n"
        val command = fileWriteCommand("src/Main.kt", text)

        assertEquals(text, decoded(command))
        // The point of the encoding: the text is not in the command.
        assertFalse(command.contains("println"))
        assertTrue(command.startsWith("printf %s '"))
    }

    /**
     * The case quoting schemes get wrong: a payload that *is* shell syntax. It must
     * survive as data, and none of it may appear in the command as itself.
     */
    @Test
    fun `shell metacharacters in the text stay data`() {
        val text = "' && rm -rf / && '$(id)`whoami`\n\ttab"
        val command = fileWriteCommand("note.txt", text)

        assertEquals(text, decoded(command))
        assertFalse(command.contains("rm -rf"))
        assertFalse(command.contains("whoami"))
    }

    /** An empty file is a legitimate save, and it must not be a special case. */
    @Test
    fun `an empty file encodes to an empty payload`() {
        val command = fileWriteCommand("empty.txt", "")

        assertEquals("", decoded(command))
    }

    /**
     * A path with a quote in it is the one input that can break out of single quotes.
     * The escape must close, emit, and reopen — and the result must still name the same
     * path to a shell.
     */
    @Test
    fun `a path containing a single quote is escaped, not dropped`() {
        val command = fileWriteCommand("it's here.txt", "x")

        // The POSIX escape sequence, present exactly once.
        assertTrue(command.contains("'it'\\''s here.txt'"))
        // ...and not the naive quoted form, which would terminate the quoting early.
        assertFalse(command.contains("'it's here.txt'"))
    }

    /** The rename only runs if the decode succeeded, so a failure cannot truncate. */
    @Test
    fun `the rename is chained behind the decode`() {
        val command = fileWriteCommand("a.txt", "x")

        assertTrue(command.contains("&& mv -f"))
        // The redirect targets the temp file, never the real one: if `base64 -d` fails,
        // the target has not been opened for writing at all.
        val redirect = command.substringAfter("base64 -d > ").substringBefore(" &&")
        assertEquals("'a.txt.dsh-save'", redirect)
        assertTrue(command.endsWith("'a.txt'"))
    }
}