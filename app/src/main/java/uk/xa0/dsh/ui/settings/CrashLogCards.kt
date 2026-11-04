package uk.xa0.dsh.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.xa0.dsh.diag.BootBeacon
import uk.xa0.dsh.diag.CrashLog
import uk.xa0.dsh.diag.CrashReport
import uk.xa0.dsh.diag.LaunchGuard
import uk.xa0.dsh.diag.NativeCrashLog
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.components.DshIconVectors
import uk.xa0.dsh.ui.list.SegmentedColumn
import uk.xa0.dsh.ui.list.SegmentedListItem
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * The Me page's **Crash log** page: what took the process down, kept so it can be handed back.
 *
 * ## Why a page and not a banner
 *
 * The two crashes this exists for had to be reproduced several times — the to-dos one on
 * every appearance of the card, the settings one on every dropdown tap. A banner is a
 * one-shot: it appears once, and if the reader dismisses it or misses it, the evidence is
 * gone. A page keeps every report (bounded, see [CrashLog.MAX_REPORTS]), so a reproduction
 * session can run as long as it needs to and every attempt is still there afterwards.
 *
 * ## Why sharing as well as copying
 *
 * A stack trace is several thousand characters and the phone is not where it gets read.
 * Copy is the fallback — it is what works with no other app installed, and it is what a
 * test can assert. Share is the path that actually moves it: the report goes out as text
 * through the system chooser, so it can land in a chat with the person who is going to
 * read it, with no cable and no `adb`.
 *
 * ## Debug only
 *
 * The list is empty in a release build because [CrashLog] is never installed there — see
 * `DshApplication`. The page still says what it is rather than drawing an empty card, so a
 * reader who opens it on a release build is told why it is empty instead of wondering
 * whether it is broken.
 */
@Composable
internal fun CrashLogSettingCards() {
    // Re-read on every composition rather than caching in a view model: the list only
    // changes when the process died and restarted, which is exactly a fresh composition.
    //
    // Both kinds in one list, newest first. They are two files in one directory because
    // they answer one question — "what killed the process" — and a reader who has to know
    // which of the two halves to open first has been given a filing system instead of an
    // answer. The names sort together because both prefixes are followed by a timestamp.
    var reports by remember { mutableStateOf(allCrashReports()) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val colors = DshTheme.colors

    // The boot record first: it is the one thing that survives a crash with no report.
    //
    // A native SIGSEGV on the render thread writes no exception, leaves no tombstone on
    // this ROM, and lands before the UI exists — so this list can be empty while the app
    // is dying every launch. The beacon's last line is what turns "it closes" into "it
    // closes at step N", and it is also on disk at
    // Android/data/<pkg>/files/diag/boot.log for a reader whose app will not stay open.
    val boot = remember { BootBeacon.read() }
    if (boot.isNotEmpty()) {
        SettingNote(
            "Last launch, step by step. The last line is the last thing that succeeded — " +
                "if the app closed, the step after it is the one that killed it.",
        )
        SegmentedColumn(
            content = listOf(
                {
                    SegmentedListItem(
                        headline = {
                            Text(
                                text = boot.trimEnd(),
                                style = DshType.micro.copy(fontFamily = FontFamily.Monospace),
                                color = colors.labelSecondary,
                            )
                        },
                    )
                },
            ),
        )
    }

    if (reports.isEmpty()) {
        SettingNote("No crashes recorded on this device.")
        SettingNote(
            "Reports are written only in debug builds. Reproduce the crash, reopen the " +
                "app, and the report appears here. Native crashes (a SIGSEGV on the " +
                "render thread, for instance) are read back from the system on the next " +
                "launch, so they appear one start later than a Java crash.",
        )
        return
    }

    // The guard's own decision, said out loud.
    //
    // A reader whose material disappeared would otherwise be looking at an app that got
    // worse for no stated reason. This is the sentence that turns it into a fact they can
    // act on — and the row below is how they undo it once the cause is gone.
    val degradedAt = LaunchGuard.degradedAt(context)
    if (degradedAt > 0L) {
        val forced = LaunchGuard.degradedLevel(context)
        SettingNote(
            "The material was stepped down automatically to [" + forced + "]: two " +
                "launches in a row ended in a crash before the app finished starting. " +
                "Each such streak drops it one level, so if this build still crashes the " +
                "next launch will drop it again. Pick a level in This device to stop the " +
                "stepping down.",
        )
        SegmentedColumn(
            content = listOf(
                {
                    SegmentedListItem(
                        onClick = {
                            LaunchGuard.reset(context)
                            reports = allCrashReports()
                        },
                        leading = {
                            Icon(
                                DshIconVectors.RefreshOutline,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headline = {
                            Text("Try the full material again", style = DshType.rowTitle)
                        },
                        supporting = {
                            Text(
                                text = "Clears the automatic fallback and returns to liquid " +
                                    "glass on the next launch.",
                                style = DshType.rowSummary,
                                color = colors.labelTertiary,
                            )
                        },
                    )
                },
            ),
        )
    }

    SettingNote(
        "Crashes recorded since this install, newest first. Tap one to read it; Share " +
            "sends the whole report to another app, Copy puts it on the clipboard.",
    )

    val rows: List<@Composable () -> Unit> = reports.map { report ->
        {
            CrashRow(
                report = report,
                expanded = open == report.file.name,
                onToggle = {
                    open = if (open == report.file.name) null else report.file.name
                },
                onCopy = { clipboard.setText(AnnotatedString(report.text)) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "DSH crash report")
                        putExtra(Intent.EXTRA_TEXT, report.text)
                    }
                    // A chooser rather than a bare ACTION_SEND: with no default handler
                    // the bare intent fails silently, and the reader is left believing
                    // the button did nothing.
                    context.startActivity(
                        Intent.createChooser(send, "Share crash report"),
                    )
                },
                onDelete = {
                    deleteCrashReport(report)
                    if (open == report.file.name) open = null
                    reports = allCrashReports()
                },
            )
        }
    }
    SegmentedColumn(title = "Recorded crashes", content = rows)

    ClearRow {
        CrashLog.clear()
        NativeCrashLog.clear()
        open = null
        reports = allCrashReports()
    }
}

/**
 * Both kinds of report, in one list.
 *
 * Sorted by file name rather than by the header's time, because both prefixes lead with a
 * sortable timestamp and the name is what the two writers already guarantee; re-parsing a
 * header to sort would be a second, weaker implementation of the same fact.
 */
private fun allCrashReports(): List<CrashReport> =
    (CrashLog.reports() + NativeCrashLog.reports()).sortedByDescending { it.file.name }

/**
 * Delete one report, wherever it came from.
 *
 * The file's own prefix says which writer owns it, and neither delete needs the other, so
 * this is a dispatch rather than a guess: a name that starts with "native-" belongs to
 * [NativeCrashLog] and everything else to [CrashLog].
 */
private fun deleteCrashReport(report: CrashReport) {
    if (report.file.name.startsWith("native-")) {
        NativeCrashLog.delete(report)
    } else {
        CrashLog.delete(report)
    }
}

/**
 * One report: its exception on the headline, its time and size underneath, and the whole
 * trace while expanded.
 *
 * The trace is drawn in a monospace face and *not* wrapped into a card of its own: a stack
 * trace's value is its column alignment, and a proportional font turns `at uk.xa0…` into
 * something that cannot be skimmed for the frame that matters.
 */
@Composable
private fun CrashRow(
    report: CrashReport,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = DshTheme.colors
    Column(Modifier.fillMaxWidth()) {
        SegmentedListItem(
            onClick = onToggle,
            leading = {
                Icon(
                    Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            },
            headline = {
                Text(
                    text = report.summary,
                    style = DshType.rowTitle,
                    color = colors.labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supporting = {
                Text(
                    text = report.time + " · " + humanBytes(report.bytes),
                    style = DshType.rowSummary,
                    color = colors.labelTertiary,
                    maxLines = 1,
                )
            },
            trailing = {
                Icon(
                    imageVector = if (expanded) DshIconVectors.ChevronUpOutline else DshIconVectors.ChevronDownOutline,
                    contentDescription = if (expanded) "Collapse report" else "Expand report",
                    tint = colors.labelTertiary,
                    modifier = Modifier.size(18.dp),
                )
            },
        )

        if (expanded) {
            Text(
                text = report.text,
                style = DshType.micro.copy(fontFamily = FontFamily.Monospace),
                color = colors.labelSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.sm),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DshSpacing.xl, vertical = DshSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionChip("Copy", Icons.Rounded.BugReport, onCopy)
                Spacer(Modifier.width(DshSpacing.sm))
                ActionChip("Share", DshIconVectors.ShareOutline, onShare)
                Spacer(Modifier.width(DshSpacing.sm))
                ActionChip("Delete", DshIconVectors.TrashOutline, onDelete)
            }
        }
    }
}

/** The page's one destructive, global action: drop every kept report. */
@Composable
private fun ClearRow(onClear: () -> Unit) {
    val colors = DshTheme.colors
    val rows: List<@Composable () -> Unit> = listOf(
        {
            SegmentedListItem(
                onClick = onClear,
                leading = {
                    Icon(
                        DshIconVectors.TrashOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
                headline = {
                    Text("Delete all reports", style = DshType.rowTitle, color = colors.error)
                },
                supporting = {
                    Text(
                        text = "Removes every recorded crash from this device.",
                        style = DshType.rowSummary,
                        color = colors.labelTertiary,
                    )
                },
            )
        },
    )
    SegmentedColumn(content = rows)
}

/** A small labelled action in a report's footer. */
@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = DshSpacing.sm, vertical = DshSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(DshSpacing.xs))
        Text(label, style = DshType.micro, color = colors.accent, maxLines = 1)
    }
}

/**
 * A report's size, for the row that has to say whether it is one screen or thirty.
 *
 * Coarse on purpose: the number's job is to tell the reader whether Share will send a
 * paragraph or a dump, and a byte count to the unit does not improve that answer.
 */
private fun humanBytes(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    else -> (bytes / (1024 * 1024)).toString() + " MB"
}
