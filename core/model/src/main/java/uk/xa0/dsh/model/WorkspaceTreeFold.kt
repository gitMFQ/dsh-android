package uk.xa0.dsh.model

/**
 * The Files tree's shape: the directories that are open, flattened into the rows
 * a list draws.
 *
 * ## Why this is a fold and not a nested composable
 *
 * A `LazyColumn` cannot nest a lazy list, and the obvious alternative — a
 * recursive composable inside a `Column` inside a `LazyColumn` item — gives up
 * recycling for every subtree at once, which is the one thing a directory tree
 * with a large `node_modules` cannot afford. Flattening is also what makes the
 * shape testable: what is open, what a tap descends into and how deep a row sits
 * are all answers about a `List<TreeRow>`, and none of them need a device.
 *
 * ## Three inputs, because they are three different facts
 *
 * [open] is what the reader expanded. [levels] is what the host has answered and
 * the caller has cached. [failures] is what it refused. Keeping them apart is not
 * tidiness: a directory can be open with no level yet (that is a *loading* row,
 * not an empty one) and can be open with a refusal (a row that stays and says
 * why). Collapsing them into one "is this level known" question would draw a
 * directory that is being fetched exactly like one that is closed, which is the
 * kind of quiet lie a tree cannot afford — the reader taps, nothing moves, and
 * nothing says a request is in flight.
 *
 * Nothing here talks to a host and nothing here is Android.
 */

/** How a directory row's children are doing. */
enum class TreeChildren {

    /** Closed. Nothing has been asked for, and nothing is drawn under the row. */
    CLOSED,

    /** Open, and the level is in flight. Drawn as a row that says so. */
    LOADING,

    /** Open, and the level is here. */
    READY,

    /**
     * Open, and the host refused it. The row stays with the refusal under it:
     * dropping the row would move every sibling and hide the fact that the
     * directory exists, which is a worse answer than showing why it will not open.
     */
    FAILED,
}

/**
 * One row of the flattened tree.
 *
 * [depth] is carried rather than derived from [path] on purpose. A path's segment
 * count is not its depth when an ancestor is filtered out, and a row indented by a
 * path it no longer displays would drift from the parent drawn above it.
 *
 * [entry] is the child as its parent's listing reported it — the name, the kind
 * and the size the row shows. [path] is that child's workspace-relative path, the
 * key the caller's cache and the session's badges are both spelled in.
 */
data class TreeRow(
    val path: String,
    val entry: WorkspaceEntry,
    val depth: Int,
    val children: TreeChildren,
    /** The open directory's level, when [children] is [TreeChildren.READY]. */
    val listing: WorkspaceListing? = null,
    /** The host's own wording, when [children] is [TreeChildren.FAILED]. */
    val failure: String? = null,
) {
    val isDirectory: Boolean get() = entry.kind == WorkspaceEntryKind.DIRECTORY

    /** Whether this row can be expanded or collapsed — only a directory can. */
    val expands: Boolean get() = isDirectory
}

/** Pure folds over one workspace's open levels. */
object WorkspaceTreeFold {

    /** The workspace root's key: the host's own spelling of "the session's root". */
    const val ROOT: String = ""

    /**
     * The rows on screen: the root level, then each open directory's children
     * directly under it, recursively.
     *
     * [levels] is keyed by the level's own workspace-relative path — the spelling
     * [WorkspaceListing.path] carries and [WorkspaceExplorer.pathOf] produces. A
     * key not reachable from the root is simply never drawn: it is a level cached
     * for a directory the reader has since closed, which is the point of keeping
     * the cache, so reopening is instant and costs no round trip.
     *
     * [badges], [showHidden], [order] and [query] are the *same* folds the flat
     * browser already applies, applied per level. That is deliberate: the tree is
     * a second way to move through one workspace, not a second set of rules about
     * what a level contains. A filter typed into the toolbar narrows every open
     * level at once.
     *
     * A directory whose *name* the filter excludes still draws when it is open and
     * something below it matches — otherwise typing a leaf's name would show the
     * leaf with no visible parent chain, and the indent would read as a bug rather
     * than as a location.
     */
    fun rows(
        levels: Map<String, WorkspaceListing>,
        open: Set<String> = emptySet(),
        failures: Map<String, String> = emptyMap(),
        badges: Map<String, SessionFile> = emptyMap(),
        showHidden: Boolean = false,
        order: ExplorerOrder = ExplorerOrder.NAME,
        query: String? = null,
    ): List<TreeRow> {
        val root = levels[ROOT] ?: return emptyList()
        val out = ArrayList<TreeRow>()
        appendLevel(
            out = out,
            levels = levels,
            open = open,
            failures = failures,
            listing = root,
            depth = 0,
            badges = badges,
            showHidden = showHidden,
            order = order,
            query = query,
        )
        return out
    }

    /**
     * Whether [path] should be drawn expanded, given what the caller holds.
     *
     * Exposed because the tap handler needs the same answer the drawing uses: a
     * directory with a refusal cached is open (so the row says why), and a
     * directory whose level is in flight is open (so the row says so). A tap must
     * not re-open something already open.
     */
    fun isOpen(path: String, open: Set<String>): Boolean = path in open

    private fun appendLevel(
        out: MutableList<TreeRow>,
        levels: Map<String, WorkspaceListing>,
        open: Set<String>,
        failures: Map<String, String>,
        listing: WorkspaceListing,
        depth: Int,
        badges: Map<String, SessionFile>,
        showHidden: Boolean,
        order: ExplorerOrder,
        query: String?,
    ) {
        val needle = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        // The filter is applied here rather than through `visibleEntries`'s query
        // argument, because that fold drops a non-matching *directory* before this
        // loop can ask whether something under it matched. An ancestor chain has to
        // survive a match below it, so the name test is this loop's job and
        // `visibleEntries` is asked only for the hidden rule and the order.
        val entries = WorkspaceExplorer.visibleEntries(
            listing = listing,
            badges = badges,
            showHidden = showHidden,
            order = order,
            query = null,
        )
        for (entry in entries) {
            val path = listing.childPath(entry)
            val directory = entry.kind == WorkspaceEntryKind.DIRECTORY
            val expanded = directory && path in open
            val matchesSelf = needle == null || entry.name.lowercase().contains(needle)
            if (!matchesSelf && !directory) continue
            if (!matchesSelf && directory) {
                // A directory that does not match its own name survives only as an
                // ancestor of a match, which requires it to be open.
                if (!expanded) continue
                if (!hasMatchingDescendant(levels, open, levels[path], badges, showHidden, needle)) continue
            }
            val childListing = if (expanded) levels[path] else null
            val failure = if (expanded) failures[path] else null
            val children = when {
                !directory -> TreeChildren.CLOSED
                !expanded -> TreeChildren.CLOSED
                failure != null -> TreeChildren.FAILED
                childListing != null -> TreeChildren.READY
                else -> TreeChildren.LOADING
            }
            out += TreeRow(
                path = path,
                entry = entry,
                depth = depth,
                children = children,
                listing = if (children == TreeChildren.READY) childListing else null,
                failure = failure,
            )
            if (children == TreeChildren.READY && childListing != null) {
                appendLevel(
                    out = out,
                    levels = levels,
                    open = open,
                    failures = failures,
                    listing = childListing,
                    depth = depth + 1,
                    badges = badges,
                    showHidden = showHidden,
                    order = order,
                    query = query,
                )
            }
        }
    }

    /**
     * Whether anything under [listing] matches [needle], at any depth.
     *
     * Depth matters: a reader who typed `build` is looking for the file in
     * `app/build/`, so the ancestor chain has to survive a match two levels down.
     * The walk is bounded by [levels], which only holds what has been opened, so it
     * never becomes a crawl of the disk — it is a walk of what is already on screen.
     */
    private fun hasMatchingDescendant(
        levels: Map<String, WorkspaceListing>,
        open: Set<String>,
        listing: WorkspaceListing?,
        badges: Map<String, SessionFile>,
        showHidden: Boolean,
        needle: String,
    ): Boolean {
        if (listing == null) return false
        val entries = WorkspaceExplorer.visibleEntries(
            listing = listing,
            badges = badges,
            showHidden = showHidden,
            order = ExplorerOrder.NAME,
            query = null,
        )
        for (entry in entries) {
            if (entry.name.lowercase().contains(needle)) return true
            if (entry.kind != WorkspaceEntryKind.DIRECTORY) continue
            val path = listing.childPath(entry)
            if (path !in open) continue
            if (hasMatchingDescendant(levels, open, levels[path], badges, showHidden, needle)) return true
        }
        return false
    }
}
