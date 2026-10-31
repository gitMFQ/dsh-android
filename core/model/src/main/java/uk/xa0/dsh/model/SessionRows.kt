package uk.xa0.dsh.model

// These rows used to live in the root package (DshViewModel.kt) while the rest of
// `model/` imported them from there, which is what kept `model/` from being
// self-contained. They are host rows, not view-model state: only the package and
// the file changed, no declaration did.

data class SessionItem(
    val id: String,
    val title: String,
    val cwd: String?,
    val updatedAt: Long,
    /**
     * Whether this session's agent is working now.
     *
     * Derived, never sampled: it is `RunningBook.running(id)` and nothing else, folded
     * in by `withRunning` at the two places a row is built or repainted. Nothing may
     * OR a live frame into it at a display site — that is what let the drawer, the
     * header chip and the lineage sheet each answer the same question differently.
     */
    val running: Boolean,
    val isSubagent: Boolean,
    /** A provisional session with nothing in it yet; the host sends `title: null`. */
    val blank: Boolean = false,
    /**
     * How many **active** scheduled tasks deliver into this session.
     *
     * The host's `schedule/catalog` answer, folded per session; zero covers both "no
     * task" and "the catalog read has never settled". A host running without the
     * Schedule bundle answers that call with an error and every row stays at zero,
     * which is exactly how the app rendered before automations existed.
     *
     * A session with a task is an ordinary session — it keeps its Workspace and its
     * row — so this is a *mark*, not a classification. The rules that read it are the
     * landing preference (see [uk.xa0.dsh.model.landingSessionId]) and the
     * idle-notification mute (see [uk.xa0.dsh.model.warrantsIdleAlert]); the drawer
     * draws it as the web's clock mark.
     */
    val scheduledTaskCount: Int = 0,
    /** Set on subagent sessions, which nest under their parent in the tree. */
    val parentSessionId: String? = null,
    /** The real subagent title, from the parent's `subagentCatalog` projection. */
    val subagentLabel: String? = null,
    /** `one-shot` | `continuable`; required by the subagent address form. */
    val subagentMode: String? = null,
    /**
     * This session's own `sessionStats` / `tokenUsage` projections, when the row carried
     * them. Null covers both "the payload had neither key" and "the fold has not been
     * materialized for this session yet" — a member whose usage the host has not given,
     * which [uk.xa0.dsh.model.FamilyUsage] reports rather than spending as a zero.
     */
    val usage: SessionStats? = null,
    /**
     * The route this session's last turn actually ran (`modelSelection.lastUsed`), which
     * is what a family cost prices its tokens at. Not the same fact as the model chip's
     * `next`, which is what the *next* turn will run.
     */
    val lastUsedModel: ModelRef? = null,
)

/**
 * One registered Workspace (a directory the host groups sessions under), with its
 * manual session order. Sourced from the `workspace/follow` stream rather than
 * derived from `cwd`, because a session's membership and order are host state.
 */
data class WorkspaceItem(
    val id: String,
    val path: String,
    val title: String,
    val sessionIds: List<String>,
)
