package uk.xa0.dsh.model

import org.json.JSONObject

/**
 * The deployment's plugin inventory: what the host has loaded, and whether each one
 * is alive.
 *
 * ## Where this comes from
 *
 * `pluginInventory/list` with no arguments, answered by
 * `@deepseek-ai/dsh-host-plugin-inventory`. The shape is read off the shipped type
 * declarations rather than guessed at:
 *
 * ```
 * PluginInventorySnapshot {
 *   managementAvailable?: boolean
 *   entries: PluginInventoryEntry[]
 *   agentPresets?: AgentPresetPluginGroup[]
 * }
 * PluginInventoryEntry {
 *   entryId: PluginEntryId
 *   moduleName: string
 *   meta?: PluginLocalizedMeta
 *   enabled: boolean
 *   fiberPhase: 'pending' | 'loading' | 'active' | 'failed' | 'unloading' | null
 *   diagnostic?: string
 * }
 * ```
 *
 * ## Why the app reads this at all
 *
 * The Plugins destination was a placeholder whose own copy said the panel protocol
 * existed and this page was where it would land. That was honest while nothing
 * consumed the inventory, but the host has been answering this endpoint the whole
 * time — so "we cannot list them" was a fact about this client, not about the
 * deployment. This is the read-only half: **list what is loaded**, not install or
 * enable anything. The write surface (`pluginManager` and its sixteen Remotes) is a
 * different feature with a different risk, and `docs/BLOCK-SCHEMA.md` puts it on the
 * permanent blocklist for the panel protocol; leaving it out of this step is the same
 * judgement applied to the page.
 *
 * ## The two facts a reader needs
 *
 * [enabled] is the Loader's *effective* answer — it already folds in a disabled
 * ancestor group — and [fiberPhase] is whether the entry's root Fiber is alive. They
 * disagree in the interesting cases, which is why the page draws them separately
 * rather than collapsing them into one "on" light: an entry can be `enabled = true`
 * with `fiberPhase = null` (enabled, not mounted in this profile) or `enabled = false`
 * with a live fiber (`unloading`). A single boolean would have to pick one of those
 * and lie about the other.
 */
data class PluginInventory(
    /**
     * Whether the deployment exposes persistent current-profile management.
     *
     * Optional on the wire, and **absent means no**, which is why it is a plain
     * boolean here: the page's only use for it is whether to say that the read-only
     * state is the deployment's own limit or this client's.
     */
    val managementAvailable: Boolean = false,
    val entries: List<PluginEntry> = emptyList(),
)

/**
 * One non-group Loader entry.
 *
 * Groups are not in this list by construction — the host's own type says "one
 * non-group Loader entry" — so there is nothing here to filter out. An entry's
 * [diagnostic] is the host's own wording for a startup failure and is drawn verbatim
 * for the same reason every other host message in this app is: paraphrasing a failure
 * is how a reader loses the string they would search for.
 */
data class PluginEntry(
    /** Stable Loader-tree identity; unique within one inventory. */
    val entryId: String,
    /** The exact module specifier the Loader imported. */
    val moduleName: String,
    /** The package's own display name, when it published one. */
    val displayName: String? = null,
    /** The package's own one-line description, when it published one. */
    val description: String? = null,
    /**
     * The Loader's effective enablement, including disabled ancestor groups.
     *
     * `true` by default on a malformed row: an entry the host listed without saying
     * whether it is enabled is one the host considers present, and drawing it as
     * disabled would be this client inventing a fact.
     */
    val enabled: Boolean = true,
    /** The root Fiber's lifecycle state, or null when it has no live root Fiber. */
    val fiberPhase: String? = null,
    /** The host's startup-failure message; absent when nothing failed. */
    val diagnostic: String? = null,
) {
    /**
     * The name to draw: the package's own display name when it published one, and the
     * module specifier otherwise.
     *
     * The specifier is the fallback rather than the default because it is usually
     * scoped and long (`@deepseek-ai/dsh-client-ui-settings-plugins`), and a reader
     * looking for "the plugins tab" is not helped by it. It is still *shown*, as the
     * row's second line, so the fallback is never information that was thrown away.
     */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: moduleName

    /**
     * Whether the entry's root Fiber is live.
     *
     * `active` only. `loading` and `unloading` are transitions rather than a settled
     * answer, and `pending` is a Fiber that has not started — none of them is "running
     * right now", which is the claim the dot makes.
     */
    val live: Boolean get() = fiberPhase == "active"

    /** The entry failed to start, and [diagnostic] says why. */
    val failed: Boolean get() = fiberPhase == "failed" || !diagnostic.isNullOrBlank()

    /**
     * The one word the row's status dot stands for.
     *
     * Deliberately four, not the host's six phases: a reader is answering "is this
     * working?" and `pending`/`loading`/`unloading` all answer "not yet, or not now"
     * in the same way. The precise phase is still available on the row, so nothing is
     * lost by the collapse — it is a summary, not a replacement.
     */
    val state: PluginState get() = when {
        failed -> PluginState.FAILED
        live -> PluginState.ACTIVE
        enabled -> PluginState.IDLE
        else -> PluginState.DISABLED
    }
}

/** What a plugin row's dot means: four states, in the order the page sorts them. */
enum class PluginState {
    /** Started and running. */
    ACTIVE,

    /** Enabled, but with no live root Fiber in this profile. */
    IDLE,

    /** Switched off — by its own row or by a disabled ancestor group. */
    DISABLED,

    /** Failed to start; the row carries the host's own message. */
    FAILED,
}

/**
 * The wire read.
 *
 * Every field is defensive: this app talks to hosts of several versions, and an
 * inventory that changed shape must degrade to a shorter list rather than to an empty
 * page with an exception behind it. A row missing its `moduleName` is dropped — it
 * cannot be named, so it cannot be drawn — while a row missing only `meta` keeps its
 * specifier as its label.
 */
object PluginInventoryWire {

    fun parse(value: JSONObject): PluginInventory {
        val management = value.optBoolean("managementAvailable", false)
        val array = value.optJSONArray("entries")
        val entries = ArrayList<PluginEntry>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                entries += parseEntry(row) ?: continue
            }
        }
        return PluginInventory(managementAvailable = management, entries = entries)
    }

    /**
     * One entry, or null when it cannot be named.
     *
     * [entryId] falls back to the module name: the id is only an identity, and two
     * entries of one module would then collide — which is a duplicate the list's key
     * would have to resolve anyway, so the fallback is the safer of the two failures.
     */
    private fun parseEntry(row: JSONObject): PluginEntry? {
        val module = row.optString("moduleName").takeIf { it.isNotBlank() } ?: return null
        val meta = row.optJSONObject("meta")
        return PluginEntry(
            entryId = row.optString("entryId").takeIf { it.isNotBlank() } ?: module,
            moduleName = module,
            displayName = meta?.optString("name")?.takeIf { it.isNotBlank() },
            description = meta?.optString("description")?.takeIf { it.isNotBlank() },
            enabled = row.optBoolean("enabled", true),
            fiberPhase = row.optString("fiberPhase").takeIf { it.isNotBlank() && it != "null" },
            diagnostic = row.optString("diagnostic").takeIf { it.isNotBlank() },
        )
    }

    /**
     * The order the page draws: **trouble first, then running, then the rest**.
     *
     * A plugin page is consulted when something is wrong, so a failed entry has to be
     * the first thing on it — burying it under twenty healthy rows is how a page
     * becomes something a reader stops opening. Within a state the order is the
     * package's own label, so the list is stable between two reads of the same
     * deployment.
     */
    fun sorted(entries: List<PluginEntry>): List<PluginEntry> =
        entries.sortedWith(
            compareBy<PluginEntry> { it.state.ordinal }.thenBy { it.label.lowercase() },
        )
}
