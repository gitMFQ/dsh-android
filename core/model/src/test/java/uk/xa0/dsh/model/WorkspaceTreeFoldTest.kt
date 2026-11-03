package uk.xa0.dsh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Files tree's flattening.
 *
 * The tree is the one part of the explorer whose shape cannot be read off a
 * screenshot: a closed directory and a directory whose level has not arrived look
 * identical in pixels, and an indent that is one level wrong still draws. So the
 * facts pinned here are the ones a capture cannot state — what is open, what a row
 * descends into, and how deep it sits.
 */
class WorkspaceTreeFoldTest {

    private fun file(name: String, size: Long? = null) =
        WorkspaceEntry(name, WorkspaceEntryKind.FILE, size)

    private fun dir(name: String) = WorkspaceEntry(name, WorkspaceEntryKind.DIRECTORY)

    private fun listing(path: String = "", vararg entries: WorkspaceEntry) =
        WorkspaceListing(path, entries.toList(), truncated = false)

    private fun rows(
        levels: Map<String, WorkspaceListing>,
        open: Set<String> = emptySet(),
        failures: Map<String, String> = emptyMap(),
        query: String? = null,
        showHidden: Boolean = false,
    ) = WorkspaceTreeFold.rows(levels, open, failures, emptyMap(), showHidden, ExplorerOrder.NAME, query)

    // ------------------------------------------------------------ the flat case

    @Test
    fun `a closed root draws one level with no children`() {
        val levels = mapOf("" to listing("", dir("out"), file("README.md")))

        val drawn = rows(levels)

        assertEquals(listOf("out", "README.md"), drawn.map { it.entry.name })
        assertEquals(listOf(0, 0), drawn.map { it.depth })
        assertEquals(TreeChildren.CLOSED, drawn.first().children)
        assertTrue(drawn.first().expands)
        assertEquals(false, drawn.last().expands)
    }

    @Test
    fun `no root level means no rows at all`() {
        assertTrue(rows(emptyMap()).isEmpty())
    }

    // ------------------------------------------------------------ opening

    @Test
    fun `an open directory puts its children directly under it, one level in`() {
        val levels = mapOf(
            "" to listing("", dir("out"), file("README.md")),
            "out" to listing("out", file("REPORT.md")),
        )

        val drawn = rows(levels, open = setOf("out"))

        assertEquals(listOf("out", "REPORT.md", "README.md"), drawn.map { it.entry.name })
        assertEquals(listOf(0, 1, 0), drawn.map { it.depth })
        assertEquals("out/REPORT.md", drawn[1].path)
        assertEquals(TreeChildren.READY, drawn[0].children)
    }

    @Test
    fun `a cached level that is not open is not drawn`() {
        val levels = mapOf(
            "" to listing("", dir("out")),
            "out" to listing("out", file("REPORT.md")),
        )

        val drawn = rows(levels, open = emptySet())

        assertEquals(listOf("out"), drawn.map { it.entry.name })
        assertEquals(TreeChildren.CLOSED, drawn[0].children)
    }

    @Test
    fun `an open directory with no level yet is loading, not closed`() {
        val drawn = rows(mapOf("" to listing("", dir("out"))), open = setOf("out"))

        assertEquals(TreeChildren.LOADING, drawn.single().children)
        assertNull(drawn.single().listing)
    }

    @Test
    fun `an open directory the host refused stays, with its wording`() {
        val drawn = rows(
            mapOf("" to listing("", dir("out"))),
            open = setOf("out"),
            failures = mapOf("out" to "workspace-file/outside-workspace"),
        )

        assertEquals(TreeChildren.FAILED, drawn.single().children)
        assertEquals("workspace-file/outside-workspace", drawn.single().failure)
    }

    @Test
    fun `a refusal outranks a stale level for the same path`() {
        // Reloading a directory that was listed before can fail; the row must not
        // keep drawing the children it no longer stands behind.
        val drawn = rows(
            mapOf("" to listing("", dir("out")), "out" to listing("out", file("REPORT.md"))),
            open = setOf("out"),
            failures = mapOf("out" to "gone"),
        )

        assertEquals(listOf("out"), drawn.map { it.entry.name })
        assertEquals(TreeChildren.FAILED, drawn.single().children)
    }

    @Test
    fun `depth nests as deep as the levels go`() {
        val levels = mapOf(
            "" to listing("", dir("a")),
            "a" to listing("a", dir("b")),
            "a/b" to listing("a/b", file("c.txt")),
        )

        val drawn = rows(levels, open = setOf("a", "a/b"))

        assertEquals(listOf("a", "b", "c.txt"), drawn.map { it.entry.name })
        assertEquals(listOf(0, 1, 2), drawn.map { it.depth })
        assertEquals("a/b/c.txt", drawn.last().path)
    }

    @Test
    fun `a level cached for a closed ancestor is unreachable`() {
        // "a" is closed but "a/b" is open: the fold must not reach through it.
        val levels = mapOf(
            "" to listing("", dir("a")),
            "a" to listing("a", dir("b")),
            "a/b" to listing("a/b", file("c.txt")),
        )

        val drawn = rows(levels, open = setOf("a/b"))

        assertEquals(listOf("a"), drawn.map { it.entry.name })
    }

    // ------------------------------------------------------------ ordering is per level

    @Test
    fun `directories lead inside every open level, not only the root`() {
        val levels = mapOf(
            "" to listing("", dir("out")),
            "out" to listing("out", file("aaa.txt"), dir("nested")),
        )

        val drawn = rows(levels, open = setOf("out"))

        assertEquals(listOf("out", "nested", "aaa.txt"), drawn.map { it.entry.name })
    }

    // ------------------------------------------------------------ the filter

    @Test
    fun `a filter narrows every open level at once`() {
        val levels = mapOf(
            "" to listing("", dir("out"), file("README.md")),
            "out" to listing("out", file("REPORT.md"), file("notes.txt")),
        )

        val drawn = rows(levels, open = setOf("out"), query = "report")

        // The directory survives because a matching child is under it; the sibling
        // that matches nothing is gone, at both depths.
        assertEquals(listOf("out", "REPORT.md"), drawn.map { it.entry.name })
    }

    @Test
    fun `a closed directory that does not match its own name is filtered out`() {
        val levels = mapOf(
            "" to listing("", dir("out"), file("README.md")),
            "out" to listing("out", file("REPORT.md")),
        )

        val drawn = rows(levels, open = emptySet(), query = "report")

        assertEquals(listOf("README.md"), drawn.map { it.entry.name })
    }

    @Test
    fun `an ancestor chain survives a match two levels down`() {
        val levels = mapOf(
            "" to listing("", dir("app")),
            "app" to listing("app", dir("build")),
            "app/build" to listing("app/build", file("bundle.zip")),
        )

        val drawn = rows(levels, open = setOf("app", "app/build"), query = "bundle")

        assertEquals(listOf("app", "build", "bundle.zip"), drawn.map { it.entry.name })
        assertEquals(listOf(0, 1, 2), drawn.map { it.depth })
    }

    @Test
    fun `a blank filter is the same as no filter`() {
        val levels = mapOf("" to listing("", dir("out"), file("README.md")))

        assertEquals(
            rows(levels).map { it.path },
            rows(levels, query = "   ").map { it.path },
        )
    }

    // ------------------------------------------------------------ hidden files

    @Test
    fun `the hidden rule applies inside open levels too`() {
        val levels = mapOf(
            "" to listing("", dir("out")),
            "out" to listing("out", file(".env"), file("REPORT.md")),
        )

        assertEquals(
            listOf("out", "REPORT.md"),
            rows(levels, open = setOf("out")).map { it.entry.name },
        )
        assertEquals(
            listOf("out", ".env", "REPORT.md"),
            rows(levels, open = setOf("out"), showHidden = true).map { it.entry.name },
        )
    }

    // ------------------------------------------------------------ the open question

    @Test
    fun `isOpen reads the same set the drawing does`() {
        assertTrue(WorkspaceTreeFold.isOpen("out", setOf("out")))
        assertEquals(false, WorkspaceTreeFold.isOpen("out", emptySet()))
        assertEquals(false, WorkspaceTreeFold.isOpen("", emptySet()))
    }
}
