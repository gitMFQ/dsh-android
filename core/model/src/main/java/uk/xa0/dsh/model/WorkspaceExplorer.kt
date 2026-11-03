package uk.xa0.dsh.model

/**
 * The workspace explorer's pure half: one directory listing, the session's own
 * touches resolved onto it, and the ordering the list draws.
 *
 * The app already had one browser — `ui/components/WorkspaceBrowser.kt` — but it
 * speaks `directoryPicker/list`, roots at the host account's home and exists to
 * pick a directory to register. This is the other surface: the web's Files tree,
 * which the host answers with `workspaceFiles/list`, scoped by session id and
 * confined to that session's workspace root. That endpoint is a **one-shot
 * call** — there is no cursor and no paging, only `maxEntries` and a `truncated`
 * flag — so a level is asked for once and cached by the caller.
 *
 * Two path vocabularies meet here and the confusion between them is the whole
 * difficulty. The transcript records a path under the tool's exact spelling —
 * often absolute, sometimes relative to the session cwd. A listing addresses a
 * child **workspace-relatively**, the way the host's own reply does ([pathOf]).
 * A badge is therefore keyed by the resolved relative path, and [badges] is the
 * one place the resolution happens: a session path that does not resolve inside
 * the workspace is dropped rather than guessed at, because the host would refuse
 * to list the directory it lives in anyway.
 *
 * Nothing here talks to a host and nothing here is Android.
 */

/** What a listed child resolves to, as the host reports it. */
enum class WorkspaceEntryKind {
    FILE,
    DIRECTORY,

    /**
     * Everything that is neither, as the host's own `other` covers it. A row is
     * drawn but is not openable: the host's `read` refuses it too.
     */
    OTHER,
}

/**
 * How the explorer orders one level.
 *
 * Three, and no "reverse" variant: each already names the direction a reader means by
 * it. [NAME] is the host's collation (natural, case-insensitive); [SIZE] puts the
 * largest file first, because "sort by size" is asked to find the big thing; [RECENT]
 * puts the file the session touched most recently first, which is a fact about the
 * transcript rather than about the disk — the listing carries no mtime.
 *
 * Directories lead in every one of them, so this is never the control that decides
 * where navigation lives.
 */
enum class ExplorerOrder {
    NAME,
    SIZE,
    RECENT,
}

/**
 * One direct child of a listed directory.
 *
 * [hidden] is derived from the name rather than carried: the host has no notion
 * of a hidden file, and the dot prefix is the one convention the web tree and
 * this app's existing browser already share.
 */
data class WorkspaceEntry(
    val name: String,
    val kind: WorkspaceEntryKind,
    /** Byte size, present only when the host reported one for a regular file. */
    val size: Long? = null,
) {
    val hidden: Boolean get() = WorkspaceExplorer.isHidden(name)
}

/**
 * One directory level as `workspaceFiles/list` answered it.
 *
 * [path] is the host's own workspace-relative spelling of the listed directory,
 * empty for the workspace root itself. [entries] are the direct children the
 * host returned; [truncated] is true when its entry cap dropped the rest.
 */
data class WorkspaceListing(
    val path: String,
    val entries: List<WorkspaceEntry>,
    val truncated: Boolean,
) {
    /** The child's workspace-relative path, by the host's own join rule. */
    fun childPath(entry: WorkspaceEntry): String = WorkspaceExplorer.pathOf(path, entry.name)
}

/**
 * What the panel is showing: a listed level, a level in flight, or a refusal.
 *
 * The requested path travels with the state because the breadcrumb and the
 * error's Retry both need it while there is no listing to read it from. A failure
 * deliberately publishes [Failed] rather than keeping the previous level: a
 * breadcrumb naming `out/sub` over `out`'s rows would be a lie about which
 * directory those rows belong to.
 */
sealed interface ExplorerState {

    /** Nothing has been asked for yet — one frame, before the first call. */
    data object Idle : ExplorerState

    /** [path] is in flight; nothing is held for it. */
    data class Loading(val path: String) : ExplorerState

    data class Ready(val listing: WorkspaceListing) : ExplorerState

    /** The host refused [path]; [message] is its own wording, or ours when it is ours. */
    data class Failed(val path: String, val message: String) : ExplorerState
}

/** One segment of the explorer's breadcrumb; [path] is workspace-relative. */
data class WorkspaceCrumb(val name: String, val path: String)

/** Pure folds over one listing: resolution, ordering, and the session's badges. */
object WorkspaceExplorer {

    /** The web tree's hidden rule, and this app's existing browser's: a dot prefix. */
    fun isHidden(name: String): Boolean = name.startsWith(".")

    /** A child's workspace-relative path: the parent joined with the name by `/`. */
    fun pathOf(parent: String, name: String): String =
        if (parent.isEmpty()) name else "$parent/$name"

    /** The final segment; the whole string when separator-free, `/` for the root. */
    fun basename(path: String): String {
        val trimmed = path.trimEnd('/')
        return if (trimmed.isEmpty()) "/" else trimmed.substringAfterLast('/')
    }

    /**
     * The workspace-relative spelling of [path], or null when it cannot be one.
     *
     * Null is the honest answer in three cases, and each has a reason to be
     * explicit rather than best-guessed:
     *
     *  - the path is absolute and not under [cwd], so the host would refuse to
     *    list the directory it is in (`workspace-file/outside-workspace`) and no
     *    row can ever carry it;
     *  - the path resolves to the workspace root itself, which is a directory,
     *    not a file row;
     *  - a `..` climbs above the root.
     *
     * `.` and empty segments are dropped, so `./out/x` and `out//x` reach the
     * same row as `out/x`. A [cwd] of null leaves only relative paths resolvable,
     * which is the correct reading: with no root there is nothing to be relative
     * to. Separators are POSIX `/`, which is what the host and the transcript
     * both use.
     */
    fun relativeToWorkspace(path: String, cwd: String?): String? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        val root = cwd?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        val relative = when {
            root != null && trimmed == root -> ""
            root != null && trimmed.startsWith("$root/") -> trimmed.removePrefix("$root/")
            trimmed.startsWith("/") -> return null
            else -> trimmed
        }
        val segments = ArrayList<String>()
        for (segment in relative.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.size - 1)
                else -> segments += segment
            }
        }
        if (segments.isEmpty()) return null
        return segments.joinToString("/")
    }

    /**
     * The session's touched files keyed by workspace-relative path.
     *
     * Two spellings of one file — the shape the host's own journals are full of,
     * where `present` names `out/report.md` and every `write` named
     * `/home/user/.../out/report.md` — resolve to one key and are merged into one
     * badge, because on the explorer's tree they are one row. [SessionFiles.derive]
     * still keeps them as two rows in its own list; that list is a record of what
     * the session said, and this one is an annotation on the filesystem.
     *
     * **A key that is absent is a file this session has not touched.** That is a
     * fact about the session, not a gap in the data: the explorer lists the
     * directory, so an unbadged row means exactly "not touched", and the panel
     * says so in words rather than leaving the reader to infer it.
     */
    fun badges(files: List<SessionFile>, cwd: String?): Map<String, SessionFile> {
        val result = LinkedHashMap<String, SessionFile>()
        for (file in files) {
            val relative = relativeToWorkspace(file.path, cwd) ?: continue
            val held = result[relative]
            result[relative] = if (held == null) file else merge(held, file)
        }
        return result
    }

    /**
     * The rows one listing draws: directories first, then by natural name order,
     * with hidden entries removed unless asked for.
     *
     * A hidden entry that carries a badge is kept even when hidden rows are off.
     * The toggle governs *discovery* — what is worth showing of a directory the
     * reader has not seen — while a badge is this session's own record, and hiding
     * a file the session edited would leave the panel's one job undone to honour a
     * filter about exploring. A hidden file the session never touched stays
     * behind the toggle, which is where the noise is.
     */
    fun visibleEntries(
        listing: WorkspaceListing,
        badges: Map<String, SessionFile>,
        showHidden: Boolean,
    ): List<WorkspaceEntry> {
        return listing.entries
            .filter { entry ->
                showHidden || !entry.hidden || badges.containsKey(listing.childPath(entry))
            }
            .sortedWith(Comparator<WorkspaceEntry> { left, right ->
                val byKind = kindRank(left.kind) - kindRank(right.kind)
                if (byKind != 0) byKind else compareNatural(left.name, right.name)
            })
    }

    /**
     * The same fold, plus the two things a reader with a large directory needs: a
     * name filter and an order other than the default.
     *
     * [query] is matched case-insensitively against the name only — not the path, and
     * not the contents. A filter that searched contents would be a different feature
     * with a different cost: the host has no content search over a directory listing,
     * and a reader typing into this field is looking for a row whose name they can
     * already half-remember. A blank query filters nothing, so `null` and `""` behave
     * alike.
     *
     * [order] never changes *which* rows appear, only their sequence, and directories
     * always lead in every order: a listing whose folders were interleaved with files
     * by size would make the one navigation affordance harder to find than the files it
     * is hiding.
     */
    fun visibleEntries(
        listing: WorkspaceListing,
        badges: Map<String, SessionFile>,
        showHidden: Boolean,
        order: ExplorerOrder,
        query: String?,
    ): List<WorkspaceEntry> {
        val needle = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val rows = listing.entries.filter { entry ->
            val visible = showHidden || !entry.hidden || badges.containsKey(listing.childPath(entry))
            // The filter applies *on top of* the hidden rule rather than instead of it:
            // a hidden file the session touched is still drawable, and a reader who
            // typed a name is asking for that row specifically.
            visible && (needle == null || entry.name.lowercase().contains(needle))
        }
        return rows.sortedWith(comparatorWithBadges(order, listing, badges))
    }

    /**
     * Largest first, with "no size" ahead of every real one.
     *
     * The rows without a size are directories and the host's `other` kind, and they
     * already lead by kind — this branch is only reached when two of them are compared,
     * where name order is the only sensible tiebreak. Among files the largest comes
     * first because that is the question "order by size" answers: a reader sorting by
     * size is looking for the big thing, not the small one.
     */
    private fun compareBySize(left: WorkspaceEntry, right: WorkspaceEntry): Int {
        val a = left.size
        val b = right.size
        return when {
            a == null && b == null -> compareNatural(left.name, right.name)
            a == null -> -1
            b == null -> 1
            else -> {
                val bySize = b.compareTo(a)
                if (bySize != 0) bySize else compareNatural(left.name, right.name)
            }
        }
    }

    /**
     * The order comparator, given the badges — the only place `lastSeq` exists.
     *
     * `RECENT` is [SessionFile.lastSeq], the transcript's own sequence number, and
     * **not** a filesystem mtime: `workspaceFiles/list` reports no mtime at all. That
     * makes the order a fact about the session rather than about the disk, which is the
     * only kind of recency this panel can honestly claim — a file the agent wrote a
     * minute ago outranks one written last week, and a file the session never touched
     * has no position at all, so it falls to the end in name order.
     */
    private fun comparatorWithBadges(
        order: ExplorerOrder,
        listing: WorkspaceListing,
        badges: Map<String, SessionFile>,
    ): Comparator<WorkspaceEntry> = Comparator { left, right ->
        val byKind = kindRank(left.kind) - kindRank(right.kind)
        if (byKind != 0) {
            byKind
        } else {
            when (order) {
                ExplorerOrder.NAME -> compareNatural(left.name, right.name)
                ExplorerOrder.SIZE -> compareBySize(left, right)
                ExplorerOrder.RECENT -> {
                    val a = badges[listing.childPath(left)]?.lastSeq
                    val b = badges[listing.childPath(right)]?.lastSeq
                    when {
                        a == null && b == null -> compareNatural(left.name, right.name)
                        a == null -> 1
                        b == null -> -1
                        else -> {
                            val bySeq = b.compareTo(a)
                            if (bySeq != 0) bySeq else compareNatural(left.name, right.name)
                        }
                    }
                }
            }
        }
    }

    /**
     * Name order the way the web tree collates it: case-insensitive, with digit
     * runs compared as numbers, so `file2` precedes `file10`.
     *
     * Equal-running numbers fall back to the shorter run first, so `file2` still
     * precedes `file02`; names that compare equal here keep the host's own order,
     * because the sort is stable.
     */
    fun compareNatural(left: String, right: String): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val a = left[i]
            val b = right[j]
            if (a.isDigit() && b.isDigit()) {
                var endA = i
                while (endA < left.length && left[endA].isDigit()) endA++
                var endB = j
                while (endB < right.length && right[endB].isDigit()) endB++
                val digitsA = left.substring(i, endA).trimStart('0')
                val digitsB = right.substring(j, endB).trimStart('0')
                val byLength = digitsA.length - digitsB.length
                if (byLength != 0) return byLength
                val byValue = digitsA.compareTo(digitsB)
                if (byValue != 0) return byValue
                val byRun = (endA - i) - (endB - j)
                if (byRun != 0) return byRun
                i = endA
                j = endB
                continue
            }
            val lowerA = a.lowercaseChar()
            val lowerB = b.lowercaseChar()
            if (lowerA != lowerB) return lowerA.compareTo(lowerB)
            i++
            j++
        }
        return (left.length - i) - (right.length - j)
    }

    /**
     * The breadcrumb from the workspace root down to [relative].
     *
     * [rootName] labels the workspace root itself, which is not a segment of the
     * relative path: there is no "up" above it, because the host confines a
     * listing to the workspace and refuses anything outside.
     */
    fun crumbs(relative: String, rootName: String): List<WorkspaceCrumb> {
        val crumbs = ArrayList<WorkspaceCrumb>()
        crumbs += WorkspaceCrumb(rootName, "")
        var running = ""
        for (segment in relative.split('/')) {
            if (segment.isEmpty()) continue
            running = if (running.isEmpty()) segment else "$running/$segment"
            crumbs += WorkspaceCrumb(segment, running)
        }
        return crumbs
    }

    private fun kindRank(kind: WorkspaceEntryKind): Int =
        if (kind == WorkspaceEntryKind.DIRECTORY) 0 else 1

    /**
     * One badge for two spellings of one file.
     *
     * Counts add, the first and last seqs are the extremes, and the touch kinds
     * are the union in first-seen order. The order is only approximately
     * chronological across the two spellings — the session's own sequence numbers
     * would be needed to interleave them, and the row shows which kinds happened
     * rather than in what order.
     */
    private fun merge(a: SessionFile, b: SessionFile): SessionFile {
        val touches = ArrayList(a.touches)
        for (touch in b.touches) if (touch !in touches) touches += touch
        return SessionFile(
            path = a.path,
            touches = touches,
            reads = a.reads + b.reads,
            writes = a.writes + b.writes,
            edits = a.edits + b.edits,
            presents = a.presents + b.presents,
            firstSeq = minOf(a.firstSeq, b.firstSeq),
            lastSeq = maxOf(a.lastSeq, b.lastSeq),
        )
    }
}
