package uk.xa0.dsh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.PluginInventoryState
import uk.xa0.dsh.R
import uk.xa0.dsh.model.PluginEntry
import uk.xa0.dsh.model.PluginInventoryWire
import uk.xa0.dsh.model.PluginState
import uk.xa0.dsh.ui.components.DshIconVectors
import uk.xa0.dsh.ui.list.SegmentedColumn
import uk.xa0.dsh.ui.list.SegmentedListItem
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The Plugins destination: what this deployment has loaded, grouped the way the web
 * groups it.
 *
 * ## What changed, and why
 *
 * The page used to be one flat "Loaded" card in state order. That is a true list and a
 * poor page: the web's plugin surfaces lead with **trouble** and then answer "what is
 * running?", so a reader opening this page when something is wrong has to be able to
 * see the broken entry without scrolling past twenty healthy ones. This page now says
 * the same thing in the same order — failed, running, enabled-but-not-mounted, off —
 * and each group is named, because a heading is what turns a sorted list into an
 * answer.
 *
 * The row keeps the web's information ladder rather than inventing one:
 *
 *  * **name** (the package's own display name, or its specifier when it published
 *    none) with the state dot, because the dot is a claim about the thing named;
 *  * **module** — the exact specifier, which is what a `dsh` command names, always
 *    shown rather than only as a fallback, so the name above can be a display name
 *    without hiding the address;
 *  * **description**, when the package published one;
 *  * **the host's own failure wording**, verbatim: a paraphrased failure is one the
 *    reader cannot search for.
 *
 * Tapping a row opens the same three facts the web's detail view carries — Source,
 * Status and, when there is one, the Reason — because the collapsed row has no space
 * for the host's six Fiber phases, and "Loading" is not the same answer as "Not
 * running".
 *
 * ## Still read-only, and deliberately
 *
 * `docs/BLOCK-SCHEMA.md` puts `pluginManager` on the permanent blocklist for the panel
 * protocol, and the same judgement applies to a page a remote phone can reach. So there
 * is no enable switch, no uninstall and no install: the page says what is loaded, and
 * the sentence at the bottom says in words that the writing half stays on the host —
 * a page that simply offers no controls leaves the reader guessing whether this app
 * cannot do it or the deployment cannot.
 */
@Composable
internal fun PluginsPage(
    state: PluginInventoryState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    // Ask on first appearance, and only then. Keyed on `Idle` rather than run
    // unconditionally, so re-entering the page with a result already held does not
    // re-ask: the inventory is a snapshot, and Refresh is the one invalidation — the
    // same rule the Files tree follows.
    LaunchedEffect(state) {
        if (state is PluginInventoryState.Idle) onRefresh()
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DshSpacing.xl)
            .padding(top = DshSpacing.lg, bottom = DshSpacing.xxxl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.plugins_title),
                style = DshType.titleMedium,
                color = colors.labelPrimary,
                modifier = Modifier.weight(1f),
            )
            // A control with an edge rather than a bare word: the page is long, and a
            // link that scrolls out of the reader's mental model is not a control.
            RefreshPill(onRefresh = onRefresh)
        }
        Spacer(Modifier.height(DshSpacing.md))
        when (state) {
            PluginInventoryState.Idle, PluginInventoryState.Loading ->
                PluginNote(stringResource(R.string.plugins_reading))

            is PluginInventoryState.Failed -> PluginFailure(state.message, onRefresh)

            is PluginInventoryState.Ready -> PluginRows(state)
        }
    }
}

/** The one write-ish control on the page, and the only one that talks to the host. */
@Composable
private fun RefreshPill(onRefresh: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(colors.codeBlock)
            .border(0.5.dp, colors.borderL1, RoundedCornerShape(DshRadius.pill))
            .clickableNoRipple(onClick = onRefresh)
            .padding(horizontal = DshSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.xs),
    ) {
        Icon(
            imageVector = DshIconVectors.RefreshOutline,
            contentDescription = null,
            tint = colors.labelSecondary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.plugins_refresh),
            style = DshType.bodySmall,
            color = colors.labelSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PluginRows(state: PluginInventoryState.Ready) {
    val colors = DshTheme.colors
    val inventory = state.inventory
    val rows = PluginInventoryWire.sorted(inventory.entries)
    if (rows.isEmpty()) {
        // An empty deployment and a failed read are different facts, and this one is
        // the host's own answer: it has nothing loaded, which is a real state on a
        // fresh install.
        PluginNote(stringResource(R.string.plugins_empty))
        return
    }
    val active = rows.count { it.state == PluginState.ACTIVE }
    val failed = rows.count { it.state == PluginState.FAILED }
    // The summary says the one thing a reader wants before scrolling: how much of this
    // is working, and whether anything needs them. A count of "twenty rows" would be
    // true and useless.
    //
    // Assembled from two resources rather than one concatenated sentence: the running
    // count is a plural (English has two forms, Chinese one) and the failure clause is
    // conditional. Concatenating inside `buildString` would bake the English word order
    // and the "·" separator into a place no translator can reach.
    val summary = buildString {
        append(pluralStringResource(R.plurals.plugins_running_summary, active, active, rows.size))
        if (failed > 0) {
            append(" · ")
            append(pluralStringResource(R.plurals.plugins_failed_summary, failed, failed))
        }
    }
    Text(
        text = summary,
        style = DshType.bodySmall,
        color = if (failed > 0) colors.error else colors.labelTertiary,
        modifier = Modifier.padding(bottom = DshSpacing.lg),
    )

    // The groups, in the order a reader needs them. Trouble leads — that is the web's
    // own ordering and the reason the sort key is `PluginState.ordinal` — and the two
    // "not doing anything" groups are separated rather than merged: *enabled but not
    // mounted* is a fact about this profile, and *switched off* is a fact about the
    // configuration. Collapsing them would tell the reader the second when the first
    // is true.
    val groups = listOf(
        PluginState.FAILED to "Needs attention",
        PluginState.ACTIVE to "Running",
        PluginState.IDLE to "Enabled, not running",
        PluginState.DISABLED to "Off",
    )
    groups.forEach { (groupState, title) ->
        val members = rows.filter { it.state == groupState }
        if (members.isEmpty()) return@forEach
        SegmentedColumn(
            title = title,
            content = members.map { entry -> { PluginRow(entry) } },
        )
        Spacer(Modifier.height(DshSpacing.lg))
    }

    // The read-only statement, in words rather than by absence. A page that simply
    // offers no controls leaves the reader guessing whether the app cannot do it or
    // the host cannot, and those are different answers.
    Text(
        text = stringResource(
            if (inventory.managementAvailable) {
                R.string.plugins_readonly_with_management
            } else {
                R.string.plugins_readonly_without_management
            },
        ),
        style = DshType.micro,
        color = colors.labelTertiary,
    )
}

/**
 * One plugin: what it is called, what it is, whether it is alive, and — on a tap — the
 * three facts the collapsed row has no room for.
 *
 * The expansion is local state keyed by the entry's id, so it survives a refresh that
 * returns the same deployment and is dropped when the row leaves the list. It is a
 * *reading* affordance rather than an action: nothing here writes to the host.
 */
@Composable
private fun PluginRow(entry: PluginEntry) {
    val colors = DshTheme.colors
    val tint = when (entry.state) {
        PluginState.ACTIVE -> colors.success
        PluginState.IDLE -> colors.labelTertiary
        PluginState.DISABLED -> colors.labelCaption
        PluginState.FAILED -> colors.error
    }
    var open by rememberSaveable(entry.entryId) { mutableStateOf(false) }
    SegmentedListItem(
        // A plugin row has no action in the web's sense: this page is an inventory, and
        // the operations a row might offer (enable, remove) are the write surface this
        // step deliberately leaves out. What the row does answer is "show me more",
        // which is why `onClick` is the expansion rather than `null`.
        onClick = { open = !open },
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.label,
                    style = DshType.labelLarge,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(DshSpacing.sm))
                // The state dot, small and still: nothing on this page is animating,
                // and a pulse on a settings list would be noise rather than liveness.
                Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(DshSpacing.xs))
                Icon(
                    imageVector = if (open) DshIconVectors.ChevronUpOutline else DshIconVectors.ChevronDownOutline,
                    contentDescription = if (open) "Hide details" else "Show details",
                    tint = colors.labelCaption,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        supporting = {
            Column {
                // The specifier, always. It is what a `dsh` command names, and it is
                // the fallback label for a package that published no display name — so
                // it is never information the page threw away.
                Text(
                    text = entry.moduleName,
                    style = DshType.micro,
                    color = colors.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.description?.let { description ->
                    Text(
                        text = description,
                        style = DshType.micro,
                        color = colors.labelSecondary,
                        maxLines = if (open) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The host's own failure wording, verbatim: a paraphrased failure is one
                // the reader cannot search for.
                entry.diagnostic?.let { message ->
                    Text(
                        text = message,
                        style = DshType.micro,
                        color = colors.error,
                        maxLines = if (open) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (open) PluginDetail(entry)
            }
        },
        trailing = {
            Text(
                text = when (entry.state) {
                    PluginState.ACTIVE -> stringResource(R.string.plugins_state_running)
                    PluginState.IDLE -> stringResource(R.string.plugins_state_idle)
                    PluginState.DISABLED -> stringResource(R.string.plugins_state_off)
                    PluginState.FAILED -> stringResource(R.string.plugins_state_failed)
                },
                style = DshType.micro,
                color = tint,
                maxLines = 1,
            )
        },
    )
}

/**
 * The expanded half of a row: Source, Status, and the Reason when there is one.
 *
 * The three are the web's detail view, minus everything that would need a write channel.
 * They are drawn as label/value pairs rather than a sentence so the two answers can be
 * compared down the column, and Status is the host's own Fiber phase rather than this
 * page's four-state summary: the summary is a reading, and this is the fact behind it.
 */
@Composable
private fun PluginDetail(entry: PluginEntry) {
    val colors = DshTheme.colors
    Column(Modifier.padding(top = DshSpacing.sm)) {
        DetailLine(label = "Source", value = entry.moduleName)
        DetailLine(label = "Status", value = phaseLabel(entry))
        entry.diagnostic?.let { DetailLine(label = "Reason", value = it, tint = colors.error) }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = DshTheme.colors
    Row(Modifier.padding(vertical = DshSpacing.xxs)) {
        Text(
            text = label,
            style = DshType.micro,
            color = colors.labelTertiary,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = value,
            style = DshType.micro,
            color = tint ?: colors.labelSecondary,
        )
    }
}

/**
 * The host's Fiber phase, in the web's own words.
 *
 * The web names all six (`rowPhasePending`, `rowPhaseLoading`, `rowPhaseActive`,
 * `rowPhaseFailed`, `rowPhaseUnloading`, `rowStateIdle`) and this is the same list,
 * because a reader comparing a phone against the web console has to be able to line the
 * two up. An unknown phase is drawn as itself rather than as "Unknown": a host newer
 * than this app should be able to say something this app has not learned yet.
 */
private fun phaseLabel(entry: PluginEntry): String = when (entry.fiberPhase) {
    "pending" -> "Waiting for dependencies"
    "loading" -> "Loading"
    "active" -> "Running"
    "failed" -> "Failed to start"
    "unloading" -> "Unloading"
    null -> if (entry.enabled) "Not running" else "Disabled"
    else -> entry.fiberPhase.orEmpty()
}

/** One sentence on an otherwise empty page, centred the way the old placeholder was. */
@Composable
private fun PluginNote(text: String, tint: androidx.compose.ui.graphics.Color? = null) {
    val colors = DshTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(vertical = DshSpacing.xxxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = DshType.bodySmall,
            color = tint ?: colors.labelTertiary,
            modifier = Modifier.padding(horizontal = DshSpacing.lg),
        )
    }
}

/** A failed read: the host's own wording, plus the one way out of it. */
@Composable
private fun PluginFailure(message: String, onRetry: () -> Unit) {
    val colors = DshTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = DshSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
    ) {
        Text(
            text = message,
            style = DshType.bodySmall,
            color = colors.error,
            modifier = Modifier.padding(horizontal = DshSpacing.lg),
        )
        Text(
            text = "Retry",
            style = DshType.bodySmall,
            color = colors.link,
            modifier = Modifier
                .clip(RoundedCornerShape(DshRadius.sm))
                .clickableNoRipple(onClick = onRetry)
                .padding(DshSpacing.xs),
        )
    }
}
