package uk.xa0.dsh.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import uk.xa0.dsh.R
import uk.xa0.dsh.PluginInventoryState
import uk.xa0.dsh.model.PluginEntry
import uk.xa0.dsh.model.PluginInventoryWire
import uk.xa0.dsh.model.PluginState
import uk.xa0.dsh.ui.list.SegmentedColumn
import uk.xa0.dsh.ui.list.SegmentedListItem
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The Plugins destination: what this deployment has loaded.
 *
 * ## What replaced the placeholder
 *
 * The page used to say the panel protocol existed and that this was where it would
 * land. That was honest while nothing consumed the inventory — but the host has been
 * answering `pluginInventory/list` the whole time, so "we cannot list them" was a fact
 * about this client rather than about the deployment. This is the read-only half:
 * **list what is loaded**, not install, enable or remove anything.
 *
 * The write surface is a different feature with a different risk, and leaving it out
 * is a decision rather than an omission: `docs/BLOCK-SCHEMA.md` puts `pluginManager`
 * on the permanent blocklist for the panel protocol, and the same judgement applies to
 * a page that a remote phone can reach.
 *
 * ## Why the rows are `SegmentedColumn`
 *
 * The same vocabulary the Me page's settings tabs use, so a reader who has seen one
 * settings page recognises this one. It also means the two surfaces cannot drift on
 * what a card looks like — including the `codeBlock` fill, which is not `bgLayer1`:
 * in this app's light palette every layer token is pure white, and a card filled with
 * one of them is invisible against the page.
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
            Text(
                text = stringResource(R.string.plugins_refresh),
                style = DshType.bodySmall,
                color = colors.link,
                modifier = Modifier
                    .clip(RoundedCornerShape(DshRadius.sm))
                    .clickableNoRipple(onClick = onRefresh)
                    .padding(DshSpacing.sm),
            )
        }
        Spacer(Modifier.height(DshSpacing.md))
        when (state) {
            PluginInventoryState.Idle, PluginInventoryState.Loading ->
                PluginNote(stringResource(R.string.plugins_reading))

            is PluginInventoryState.Failed ->
                PluginNote(state.message, tint = colors.error)

            is PluginInventoryState.Ready -> PluginRows(state)
        }
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
    SegmentedColumn(
        title = stringResource(R.string.plugins_loaded),
        content = rows.map { entry -> { PluginRow(entry) } },
    )
    Spacer(Modifier.height(DshSpacing.lg))
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

/** One plugin: what it is called, what it is, and whether it is alive. */
@Composable
private fun PluginRow(entry: PluginEntry) {
    val colors = DshTheme.colors
    val tint = when (entry.state) {
        PluginState.ACTIVE -> colors.success
        PluginState.IDLE -> colors.labelTertiary
        PluginState.DISABLED -> colors.labelCaption
        PluginState.FAILED -> colors.error
    }
    SegmentedListItem(
        // A plugin row has no action: this page is an inventory, and the operations a
        // row might offer (enable, remove) are the write surface this step deliberately
        // leaves out. `onClick = null` is what makes that structural rather than a
        // disabled-looking button.
        onClick = null,
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.label,
                    style = DshType.labelLarge,
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(DshSpacing.sm))
                // The state dot, small and still: nothing on this page is animating,
                // and a pulse on a settings list would be noise rather than liveness.
                Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
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
                        maxLines = 2,
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
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
