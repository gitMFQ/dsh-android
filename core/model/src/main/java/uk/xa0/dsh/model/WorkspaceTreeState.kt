package uk.xa0.dsh.model

/**
 * What the Files tree holds: the levels the host has answered, which directories
 * are open, and the ones it refused.
 *
 * Kept as three separate maps/sets rather than one map of "known level" objects
 * because they are three different facts about a directory, and the tree has to
 * draw them differently: open-and-arrived is a level, open-and-in-flight is a row
 * that says so, open-and-refused is a row that says why. A single `Map<String,
 * WorkspaceListing?>` cannot tell "closed" from "being fetched", and a tree that
 * draws those the same way leaves the reader tapping a row that never responds.
 *
 * [root] is the session's workspace root as the host spelled it, so a row's
 * absolute path can be shown without the UI re-deriving the join.
 */
data class WorkspaceTreeState(
    /** Levels by their workspace-relative path; `""` is the session root. */
    val levels: Map<String, WorkspaceListing> = emptyMap(),
    /** Directories the reader has expanded. */
    val open: Set<String> = emptySet(),
    /** Directories the host refused, by path, with its own wording. */
    val failures: Map<String, String> = emptyMap(),
    /** The workspace root as the host spells it, for absolute paths in the UI. */
    val root: String = "",
) {
    /** Whether anything has been listed yet — the tree's "has it started" question. */
    val isEmpty: Boolean get() = levels.isEmpty()

    /**
     * The rows the tree draws, with the explorer's own ordering and filter rules
     * applied per level.
     *
     * A method rather than a free function so the call site cannot pair the levels
     * with the wrong open set: they are one value precisely because they are only
     * meaningful together.
     */
    fun rows(
        badges: Map<String, SessionFile> = emptyMap(),
        showHidden: Boolean = false,
        order: ExplorerOrder = ExplorerOrder.NAME,
        query: String? = null,
    ): List<TreeRow> = WorkspaceTreeFold.rows(
        levels = levels,
        open = open,
        failures = failures,
        badges = badges,
        showHidden = showHidden,
        order = order,
        query = query,
    )

    /** [path]'s level, or null when it has not arrived (or was refused). */
    fun levelOf(path: String): WorkspaceListing? = levels[path]

    /** The row for [path], when the tree is drawing it. */
    fun rowOf(path: String): TreeRow? = rows().firstOrNull { it.path == path }
}
