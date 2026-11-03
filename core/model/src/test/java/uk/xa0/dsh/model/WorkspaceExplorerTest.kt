package uk.xa0.dsh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explorer's pure half: resolving session spellings onto workspace-relative
 * paths, merging two spellings into one badge, and the order a level draws in.
 *
 * ## What these can and cannot show
 *
 * They show the folds' own answers: which spellings resolve and which are
 * refused, which touch kinds one badge carries, and which rows a toggle keeps.
 * They cannot show that the host lists a directory at all — `workspaceFiles/list`
 * is an RPC and this suite has no host — nor that the panel *draws* any of it,
 * which is `ui/FilesPanelExplorerTest`. Two spellings merged into one badge is
 * this file's claim about the tree; that `SessionFiles.derive` still keeps them
 * as two rows is `SessionFilesTest`'s.
 */
class WorkspaceExplorerTest {

    private val cwd = "/w"

    private fun touched(
        path: String,
        touches: List<FileTouch>,
        reads: Int = 0,
        writes: Int = 0,
        edits: Int = 0,
        presents: Int = 0,
        firstSeq: Int = 1,
        lastSeq: Int = 1,
    ) = SessionFile(
        path = path,
        touches = touches,
        reads = reads,
        writes = writes,
        edits = edits,
        presents = presents,
        firstSeq = firstSeq,
        lastSeq = lastSeq,
    )

    private fun entry(name: String, kind: WorkspaceEntryKind = WorkspaceEntryKind.FILE) =
        WorkspaceEntry(name, kind)

    // ------------------------------------------------------ path resolution

    @Test
    fun `an absolute path under the cwd resolves to a workspace path`() {
        assertEquals("out/x.md", WorkspaceExplorer.relativeToWorkspace("/w/out/x.md", cwd))
    }

    @Test
    fun `the cwd itself is not a file row`() {
        assertNull(WorkspaceExplorer.relativeToWorkspace("/w", cwd))
    }

    @Test
    fun `an absolute path outside the cwd is refused, not guessed at`() {
        // The host confines a listing to the workspace, so no row can ever carry
        // this path; a badge for it would be a mark on nothing.
        assertNull(WorkspaceExplorer.relativeToWorkspace("/elsewhere/x.md", cwd))
        assertNull(WorkspaceExplorer.relativeToWorkspace("/wider/x.md", cwd))
    }

    @Test
    fun `a relative path is already a workspace path`() {
        assertEquals("out/x.md", WorkspaceExplorer.relativeToWorkspace("out/x.md", cwd))
        assertEquals("out/x.md", WorkspaceExplorer.relativeToWorkspace("./out/x.md", cwd))
        assertEquals("x.md", WorkspaceExplorer.relativeToWorkspace("out/../x.md", cwd))
        assertEquals("out/x.md", WorkspaceExplorer.relativeToWorkspace("out//x.md", cwd))
    }

    @Test
    fun `a parent segment above the root is refused`() {
        assertNull(WorkspaceExplorer.relativeToWorkspace("../x.md", cwd))
        assertNull(WorkspaceExplorer.relativeToWorkspace("out/../../x.md", cwd))
    }

    @Test
    fun `with no cwd only relative spellings resolve`() {
        assertEquals("out/x.md", WorkspaceExplorer.relativeToWorkspace("out/x.md", null))
        assertNull(WorkspaceExplorer.relativeToWorkspace("/w/out/x.md", null))
    }

    // ------------------------------------------------------------- badges

    @Test
    fun `two spellings of one file are one badge`() {
        val badges = WorkspaceExplorer.badges(
            listOf(
                touched("/w/out/report.md", listOf(FileTouch.READ), reads = 1, firstSeq = 1, lastSeq = 1),
                touched("out/report.md", listOf(FileTouch.WRITTEN), writes = 2, firstSeq = 2, lastSeq = 5),
            ),
            cwd,
        )

        assertEquals(1, badges.size)
        val badge = badges.getValue("out/report.md")
        assertEquals(1, badge.reads)
        assertEquals(2, badge.writes)
        assertEquals(listOf(FileTouch.READ, FileTouch.WRITTEN), badge.touches)
        assertEquals(1, badge.firstSeq)
        assertEquals(5, badge.lastSeq)
        assertTrue(badge.modified)
    }

    @Test
    fun `a file the session never touched has no key, which is the fact it states`() {
        val badges = WorkspaceExplorer.badges(
            listOf(touched("/w/out/report.md", listOf(FileTouch.READ), reads = 1)),
            cwd,
        )

        // Absence is not "unknown": the explorer lists the directory, so a row
        // with no key under its path is a file this session has not touched.
        assertFalse(badges.containsKey("out/other.md"))
        assertEquals(setOf("out/report.md"), badges.keys)
    }

    @Test
    fun `a path outside the workspace contributes no badge`() {
        val badges = WorkspaceExplorer.badges(
            listOf(touched("/elsewhere/report.md", listOf(FileTouch.WRITTEN), writes = 1)),
            cwd,
        )

        assertTrue(badges.isEmpty())
    }

    // ------------------------------------------------------------ ordering

    private fun level(vararg entries: WorkspaceEntry) =
        WorkspaceListing(path = "", entries = entries.toList(), truncated = false)

    @Test
    fun `directories come first and names sort naturally`() {
        val rows = WorkspaceExplorer.visibleEntries(
            level(
                entry("file10.txt"),
                entry("src", WorkspaceEntryKind.DIRECTORY),
                entry("file2.txt"),
                entry("app", WorkspaceEntryKind.DIRECTORY),
            ),
            badges = emptyMap(),
            showHidden = false,
        )

        assertEquals(
            listOf("app", "src", "file2.txt", "file10.txt"),
            rows.map { it.name },
        )
    }

    @Test
    fun `a hidden entry is behind the toggle unless the session touched it`() {
        val listing = level(
            entry(".work", WorkspaceEntryKind.DIRECTORY),
            entry(".env"),
            entry("notes.txt"),
        )
        val badges = mapOf("notes.txt" to touched("notes.txt", listOf(FileTouch.READ), reads = 1))

        val hiddenOff = WorkspaceExplorer.visibleEntries(listing, badges, showHidden = false)
        assertEquals(listOf("notes.txt"), hiddenOff.map { it.name })

        val hiddenOn = WorkspaceExplorer.visibleEntries(listing, badges, showHidden = true)
        assertEquals(listOf(".work", ".env", "notes.txt"), hiddenOn.map { it.name })
    }

    @Test
    fun `a hidden file the session touched is shown with hidden rows off`() {
        val listing = level(entry(".work/plan.md"), entry("README.md"))
        val badges = mapOf(
            ".work/plan.md" to touched(".work/plan.md", listOf(FileTouch.EDITED), edits = 1),
        )

        val rows = WorkspaceExplorer.visibleEntries(listing, badges, showHidden = false)

        assertEquals(listOf(".work/plan.md", "README.md"), rows.map { it.name })
    }

    @Test
    fun `a child's path is the listed path joined with its name`() {
        val nested = WorkspaceListing(
            path = "out",
            entries = listOf(entry("report.md")),
            truncated = false,
        )

        assertEquals("out/report.md", nested.childPath(nested.entries.single()))
        assertEquals("report.md", WorkspaceListing("", listOf(entry("report.md")), false)
            .childPath(entry("report.md")))
    }

    @Test
    fun `natural comparison holds the web tree's two cases`() {
        assertTrue(WorkspaceExplorer.compareNatural("file2", "file10") < 0)
        assertTrue(WorkspaceExplorer.compareNatural("file10", "file2") > 0)
        assertTrue(WorkspaceExplorer.compareNatural("File2", "file10") < 0)
        assertEquals(0, WorkspaceExplorer.compareNatural("same", "same"))
    }

    // ----------------------------------------------------------- breadcrumbs

    @Test
    fun `the breadcrumb roots at the workspace name, with no way above it`() {
        val crumbs = WorkspaceExplorer.crumbs("out/deep", "project")

        assertEquals(listOf("project", "out", "deep"), crumbs.map { it.name })
        assertEquals(listOf("", "out", "out/deep"), crumbs.map { it.path })
    }

    @Test
    fun `the breadcrumb of the root is the root alone`() {
        val crumbs = WorkspaceExplorer.crumbs("", "project")

        assertEquals(listOf("project"), crumbs.map { it.name })
    }

    @Test
    fun `a basename survives a trailing separator and a bare root`() {
        assertEquals("project", WorkspaceExplorer.basename("/home/user/project"))
        assertEquals("project", WorkspaceExplorer.basename("/home/user/project/"))
        assertEquals("/", WorkspaceExplorer.basename("/"))
    }

    // ------------------------------------------------------ order and filter

    /** A file entry carrying a size, for the orders that need one. */
    private fun sized(name: String, size: Long) =
        WorkspaceEntry(name, WorkspaceEntryKind.FILE, size = size)

    private fun names(
        listing: WorkspaceListing,
        badges: Map<String, SessionFile> = emptyMap(),
        order: ExplorerOrder = ExplorerOrder.NAME,
        query: String? = null,
        showHidden: Boolean = false,
    ) = WorkspaceExplorer
        .visibleEntries(listing, badges, showHidden, order, query)
        .map { it.name }

    @Test
    fun `size order puts the largest file first`() {
        val listing = level(
            sized("small.txt", 10),
            sized("huge.bin", 9_000),
            sized("medium.log", 500),
        )

        assertEquals(
            listOf("huge.bin", "medium.log", "small.txt"),
            names(listing, order = ExplorerOrder.SIZE),
        )
    }

    @Test
    fun `size order keeps directories ahead of every file`() {
        // A directory has no size at all. It must not be treated as the smallest file:
        // it is the row that cannot be sorted by size, and it is also the only
        // navigation affordance in the list.
        val listing = level(
            sized("huge.bin", 9_000),
            entry("src", WorkspaceEntryKind.DIRECTORY),
            sized("small.txt", 10),
        )

        assertEquals(
            listOf("src", "huge.bin", "small.txt"),
            names(listing, order = ExplorerOrder.SIZE),
        )
    }

    @Test
    fun `recent order follows the transcript, not the disk`() {
        val listing = level(sized("old.txt", 1), sized("new.txt", 1), sized("mid.txt", 1))
        val badges = mapOf(
            "old.txt" to touched("old.txt", listOf(FileTouch.READ), firstSeq = 1, lastSeq = 3),
            "new.txt" to touched("new.txt", listOf(FileTouch.READ), firstSeq = 9, lastSeq = 42),
            "mid.txt" to touched("mid.txt", listOf(FileTouch.READ), firstSeq = 5, lastSeq = 11),
        )

        assertEquals(
            listOf("new.txt", "mid.txt", "old.txt"),
            names(listing, badges, order = ExplorerOrder.RECENT),
        )
    }

    @Test
    fun `a file the session never touched sorts last in recent order`() {
        // It has no position in the transcript at all, which is not the same as "old".
        // Putting it first would claim the session had touched it before everything.
        val listing = level(sized("touched.txt", 1), sized("untouched.txt", 1))
        val badges = mapOf(
            "touched.txt" to touched("touched.txt", listOf(FileTouch.READ), lastSeq = 7),
        )

        assertEquals(
            listOf("touched.txt", "untouched.txt"),
            names(listing, badges, order = ExplorerOrder.RECENT),
        )
    }

    @Test
    fun `the filter matches the name case-insensitively`() {
        val listing = level(sized("README.md", 1), sized("notes.md", 1), sized("readme.txt", 1))

        assertEquals(listOf("README.md", "readme.txt"), names(listing, query = "read"))
        assertEquals(listOf("README.md", "readme.txt"), names(listing, query = "READ"))
    }

    @Test
    fun `a blank filter filters nothing`() {
        val listing = level(sized("a.txt", 1), sized("b.txt", 1))

        assertEquals(listOf("a.txt", "b.txt"), names(listing, query = null))
        assertEquals(listOf("a.txt", "b.txt"), names(listing, query = ""))
        assertEquals(listOf("a.txt", "b.txt"), names(listing, query = "   "))
    }

    @Test
    fun `the filter is applied on top of the hidden rule, not instead of it`() {
        // A hidden file the session never touched is not drawable at all, so typing its
        // name must not surface it: the filter narrows what may be drawn, it does not
        // widen it.
        val listing = level(sized(".env", 1), sized("env.txt", 1))

        assertEquals(listOf("env.txt"), names(listing, query = "env"))
        assertEquals(listOf(".env", "env.txt"), names(listing, showHidden = true, query = "env"))
    }

    @Test
    fun `the order never changes which rows appear`() {
        val listing = level(
            sized("b.txt", 9),
            entry("src", WorkspaceEntryKind.DIRECTORY),
            sized("a.txt", 1),
        )

        val byName = names(listing, order = ExplorerOrder.NAME)
        val bySize = names(listing, order = ExplorerOrder.SIZE)
        val byRecent = names(listing, order = ExplorerOrder.RECENT)

        assertEquals(byName.toSet(), bySize.toSet())
        assertEquals(byName.toSet(), byRecent.toSet())
        // ...and the sequence is what actually differs, or this test would pass on an
        // implementation that ignored the parameter.
        assertEquals(listOf("src", "a.txt", "b.txt"), byName)
        assertEquals(listOf("src", "b.txt", "a.txt"), bySize)
    }
}
