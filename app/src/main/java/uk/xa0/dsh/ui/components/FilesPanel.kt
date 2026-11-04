package uk.xa0.dsh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import uk.xa0.dsh.diag.DiagLazyList
import uk.xa0.dsh.diag.DiagScrollColumn
import uk.xa0.dsh.diag.diagDrag
import uk.xa0.dsh.diag.diagOffset
import uk.xa0.dsh.model.FileDownloader
import uk.xa0.dsh.model.FilePreview
import uk.xa0.dsh.model.FileTouch
import uk.xa0.dsh.model.ExplorerOrder
import uk.xa0.dsh.model.ExplorerState
import uk.xa0.dsh.model.TreeChildren
import uk.xa0.dsh.model.TreeRow
import uk.xa0.dsh.model.WorkspaceTreeState
import uk.xa0.dsh.model.PreviewFormat
import uk.xa0.dsh.model.PreviewLine
import uk.xa0.dsh.model.SessionFile
import uk.xa0.dsh.model.WorkspaceEntry
import uk.xa0.dsh.model.WorkspaceEntryKind
import uk.xa0.dsh.model.WorkspaceCrumb
import uk.xa0.dsh.model.WorkspaceExplorer
import uk.xa0.dsh.model.formatSize
import uk.xa0.dsh.model.isMarkdownPath
import uk.xa0.dsh.model.previewFormatOf
import uk.xa0.dsh.model.previewLanguageOf
import uk.xa0.dsh.model.WorkspaceFileLoader
import uk.xa0.dsh.ui.clickableNoRipple
import uk.xa0.dsh.ui.glass.GlassBarBackdrop
import uk.xa0.dsh.ui.glass.glassLift
import uk.xa0.dsh.ui.glass.liquidPane
import uk.xa0.dsh.ui.markdown.MarkdownText
import uk.xa0.dsh.ui.markdown.highlightCodeLine
import uk.xa0.dsh.ui.overlay.ConfirmDialogHost
import uk.xa0.dsh.ui.overlay.ConfirmDialogVisuals
import uk.xa0.dsh.ui.overlay.ConfirmTone
import uk.xa0.dsh.ui.shortenPath
import uk.xa0.dsh.ui.theme.DshColors
import uk.xa0.dsh.ui.theme.DshRadius
import uk.xa0.dsh.ui.theme.DshSpacing
import uk.xa0.dsh.ui.theme.DshTheme
import uk.xa0.dsh.ui.theme.DshType

/**
 * What the parent must supply for the panel's explorer half.
 *
 * [onList] takes a **workspace-relative** directory, `""` for the session root,
 * which is what the host's own listing reply names; the parent sends the root as
 * the host's own `.`, because an empty `path` is refused.
 * [onReload] re-lists the level on screen, ignoring any cache — the one
 * invalidation the tree has, since `workspaceFiles/list` is a one-shot call and
 * the tree subscribes to no watch.
 */
data class ExplorerWiring(
    val state: ExplorerState,
    val onList: (String) -> Unit,
    val onReload: () -> Unit,
    /**
     * The tree: every level the host has answered, and which directories are open.
     *
     * Separate from [state] because the two answer different questions — see
     * `DshViewModel.workspaceTree`. Null means the parent has no tree (a host-free
     * caller, or a test that only exercises the flat pane), and then the pane draws
     * the single level [state] carries, exactly as it did before the tree existed.
     * That fallback is deliberate: it keeps every existing caller and every existing
     * test working unchanged, and it makes the tree an addition rather than a
     * rewrite of the one surface that already worked.
     */
    val tree: WorkspaceTreeState? = null,
    /** Expand or collapse one directory. Null draws directories as descend-only. */
    val onToggle: ((String) -> Unit)? = null,
)

/**
 * The session's Files panel: the files the transcript touched, and the workspace
 * they live in, with a numbered text preview for the selected one.
 *
 * Two lists over one session, and the difference between them is the point:
 *
 *  - **This session** is `SessionFiles.derive(entries)` — the files the
 *    transcript actually touched, in first-seen order. It is true and it is
 *    small, and it stays: it is the only list that records what the session said,
 *    under the exact spelling a tool call used, which is the spelling a
 *    deliverable card carries and the one the preview's transcript cache is keyed
 *    by. It is not required to open a card's focus — see [initialPath] — but it is
 *    what makes the file *this session's* rather than merely present.
 *  - **Browse** is the workspace tree, listed one directory at a time from
 *    `workspaceFiles/list` rooted at the session's cwd, with the session's own
 *    touches drawn on it as marks. The user's complaint was that the panel held
 *    only a handful of rows and read as broken rather than scoped; a directory
 *    listing is what makes it read as the workspace, and the marks are what keep
 *    it about *this* session.
 *
 * A mark means the session read, wrote, edited, or declared that exact path. **A
 * row with no mark is a file this session has not touched** — a fact, not a gap:
 * the row is in the list because it is in the directory, and the panel states the
 * convention in words rather than leaving the absence to be read as "unknown".
 * That is the same distinction the present-as-a-row change was made for.
 *
 * The composition stays data-in / callback-out so the panel is host-free:
 *
 *  - [cached] is `SessionFiles.previews(entries)` — content the durable
 *    transcript already carries, keyed by exact path. It is the first choice,
 *    matching the web's resolution order (the session resource answers before
 *    the workspace file), and it works with no host at all.
 *  - [loader] is the on-demand workspace read (`workspaceFiles/read`). It is
 *    asked only for a path the transcript holds nothing for, and its outcome is
 *    memoized for the panel's lifetime, so re-selecting a file never refetches.
 *    Failure renders the host's own wording rather than a substitute.
 *  - [explorer] is the workspace listing and the way to move it. Null means the
 *    parent has no host (or no cwd), and then there is no Browse mode at all
 *    rather than a tab that can only be empty.
 *  - [downloader] is the on-demand whole-file read, offered as a Download control
 *    in the preview header. It is wired only by the parent that has a session to
 *    read from; a panel without one simply does not offer it, rather than drawing
 *    a control that cannot work.
 *  - [onSaveFile] is the write half, and it is the one new capability this panel
 *    gained: a text page the host delivered in full opens in a monospace editor, and
 *    Save asks once and then hands `(path, text)` back. A windowed or truncated page
 *    is deliberately *not* editable — saving it would write a part of the file back
 *    over the whole of it — and neither is a picture or a failed read. Null draws no
 *    Edit control at all.
 *  - [backdrop] is the panel’s own glass, sampled by the header pills. Null paints
 *    the flat material, which is what every caller that has not wired one gets.
 *
 * The preview draws a file three ways, and says which: a document's *rendering*
 * (Markdown), a document's source with its code coloured, and a picture. Rendering
 * replaces the raw text, so it is a claim about the content and never the only view —
 * a Markdown file opens with a Source toggle beside it, and a page the host cut short
 * says so above whichever view is showing. Those two belong to the *preview* rather
 * than to either list, so they behave the same in both modes: a file reached through
 * Browse is the same file, and a rendering that dropped its toggle there would be the
 * one place the claim went unqualified.
 *
 * On a phone the list and preview swap in place; from 720dp they sit side by
 * side, which is the desktop reading of the web's two-pane right sidebar.
 */
@Composable
fun FilesPanel(
    files: List<SessionFile>,
    modifier: Modifier = Modifier,
    /** Session cwd, shown as the list's header crumb and the explorer's root. */
    root: String? = null,
    /** Content from tool results, keyed by exact path. */
    cached: Map<String, FilePreview> = emptyMap(),
    /** On-demand workspace read; null means "use [cached] only". */
    loader: WorkspaceFileLoader? = null,
    /** On-demand whole-file read for the Download control; null draws none. */
    downloader: FileDownloader? = null,
    /**
     * Path to open straight away, for a caller that arrived from a file link or a
     * deliverable card rather than from this list. It is opened even when the
     * derivation has no row for it: such a path is a fact about the transcript,
     * not about this list, and [loader] resolves it against the session all the
     * same. Discarding it was the silent no-op the reader met — the panel came up
     * on its list and the file never opened.
     *
     * Naming a file also opens the panel on **This session** rather than Browse,
     * because that is the mode whose rows are addressed the way a card's path is.
     */
    initialPath: String? = null,
    /** Fired when a file row is selected, for a parent that wants to observe it. */
    onSelect: ((SessionFile) -> Unit)? = null,
    /** The workspace listing, when the parent has a host to list with. */
    explorer: ExplorerWiring? = null,
    /**
     * Writes a file back: `(path, text)`, with the path exactly as the preview
     * names it. Null draws no Edit control at all.
     *
     * Optional for the same reason [loader] and [downloader] are: this panel is
     * host-free data-in / callback-out, and a caller with no write channel must not
     * be offered a button that cannot work. It also keeps every existing caller and
     * test source-compatible — the parameter defaults to null.
     *
     * The panel does not wait for the result and does not pretend the write
     * happened: it leaves edit mode and lets the parent’s own reload decide what the
     * file now says. A write that fails therefore keeps the previous preview rather
     * than showing text the host never stored.
     */
    onSaveFile: ((path: String, text: String) -> Unit)? = null,
    /**
     * The backdrop the panel’s glass header samples, or null on the flat tiers.
     *
     * A *panel* backdrop rather than the screen’s: the header floats over this pane,
     * so what it should blur is the file list under it, not the conversation behind
     * the pane. Null is the honest answer for a caller that never wired one, and the
     * header then paints the flat material exactly as the rest of the chrome does.
     */
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    val canExplore = explorer != null && root != null
    // Which list is up. The workspace is the default — a panel derived only from
    // the session's own activity is what read as broken — but a caller that named
    // a file arrived from a card or a link, and the session list is the one whose
    // rows are addressed the way a card's path is, so that opens as it did.
    var browse by rememberSaveable(initialPath, canExplore) {
        mutableStateOf(canExplore && initialPath == null)
    }
    // The path on screen: [initialPath], or a row the reader tapped. A focus the
    // derivation has no row for is kept rather than discarded: such a path is a
    // fact about the transcript, not about this list, and the loader resolves it
    // against the session all the same.
    var selected by remember(initialPath) { mutableStateOf(initialPath) }
    // True once the *session list* has held the current selection, so a path that
    // was a row and is no longer one (a transcript reload) is still dropped, while
    // a path only an outside caller knew is not.
    var listed by remember(initialPath) { mutableStateOf(false) }
    // The session's own record, resolved onto workspace-relative paths once per
    // list rather than per row.
    val badges = remember(files, root) { WorkspaceExplorer.badges(files, root) }

    // The drop-on-reload guard belongs to the session list, and only to it: a
    // browse selection is a workspace path this list is not drawn from, and a mode
    // switch clears the selection outright.
    LaunchedEffect(files, selected, browse) {
        if (browse) return@LaunchedEffect
        val held = selected != null && files.any { it.path == selected }
        if (held) listed = true else if (listed) selected = null
    }

    // Ask for the root when Browse has nothing to show. Guarded on the state
    // rather than run on every toggle, so a reader who was two directories down
    // finds the tree where they left it after looking at the session list; a
    // freshly opened panel is `Idle` (the parent resets on close), so that is
    // where the session root is listed. Idleness is a key as well as a condition,
    // or a reset that arrives while Browse is already up would leave the pane
    // saying "Loading…" with nothing in flight.
    val explorerIdle = explorer?.state is ExplorerState.Idle
    LaunchedEffect(browse, root, explorerIdle) {
        val wiring = explorer
        if (browse && explorerIdle && wiring != null && root != null) wiring.onList("")
    }

    // Workspace reads are memoized for the panel's lifetime: re-selecting a file
    // must not refetch, and a failure keeps the host's message on screen until
    // Retry. `Loading` is stored too, so one map lookup answers the body.
    val fetched = remember { mutableStateMapOf<String, FilePreview>() }
    var reloadKey by remember { mutableStateOf(0) }
    // The effect keys on the selection, not on `loader`: a caller that rebuilds
    // the interface each composition must not cancel and restart a live read.
    val currentLoader by rememberUpdatedState(loader)

    // The transcript keys content by its own spelling of the path; the explorer
    // addresses the same file workspace-relatively. Preferring the transcript's
    // key when the row carries a badge means a file the session already read is
    // previewed from the transcript instead of fetched a second time. The *read*
    // still travels under the path on screen, which is what the host resolves.
    val readKey: String? = selected?.let { path -> badges[path]?.path ?: path }

    LaunchedEffect(readKey, reloadKey) {
        val key = readKey ?: return@LaunchedEffect
        val path = selected ?: return@LaunchedEffect
        val read = currentLoader ?: return@LaunchedEffect
        if (cached.containsKey(key) || fetched.containsKey(key)) return@LaunchedEffect
        fetched[key] = FilePreview.Loading
        try {
            fetched[key] = read.load(path)
        } catch (cancelled: CancellationException) {
            // The selection moved on: leave nothing behind, so returning to the
            // file fetches again instead of sticking on "Reading…".
            fetched.remove(key)
            throw cancelled
        } catch (error: Throwable) {
            fetched[key] = FilePreview.Unavailable(
                error.message?.takeIf { it.isNotBlank() } ?: "Could not read this file.",
            )
        }
    }

    val shown: FilePreview? = readKey?.let { key -> cached[key] ?: fetched[key] }
    val retry: () -> Unit = { readKey?.let { key -> fetched.remove(key); reloadKey += 1 } }

    // Nothing to list *and* nothing asked for: the panel's own empty state. A path
    // an outside caller asked for goes on to the preview instead, which is the one
    // thing this panel can still show for a session whose derivation holds nothing.
    // Browse never takes this branch: its content is the tree, not the derivation.
    if (files.isEmpty() && selected == null && !browse) {
        Box(modifier.fillMaxSize().background(colors.bgBase), contentAlignment = Alignment.Center) {
            FilesEmptyState()
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bgBase)) {
        val twoPane = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            if (canExplore) {
                FilesModeTabs(
                    browse = browse,
                    onBrowse = { next ->
                        if (next != browse) {
                            browse = next
                            // The two lists address the same file under different
                            // spellings, so a selection made in one is not a
                            // selection in the other. `listed` is part of that
                            // selection: only the session list ever sets it.
                            selected = null
                            listed = false
                        }
                    },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val list: @Composable (Modifier) -> Unit = { listModifier ->
                    if (browse && explorer != null && root != null) {
                        ExplorerPane(
                            root = root,
                            state = explorer.state,
                            badges = badges,
                            selectedPath = selected,
                            onList = explorer.onList,
                            onReload = explorer.onReload,
                            onSelectFile = { path, badge ->
                                selected = path
                                badge?.let { onSelect?.invoke(it) }
                            },
                            modifier = listModifier,
                            tree = explorer.tree,
                            onToggle = explorer.onToggle,
                            backdrop = backdrop,
                        )
                    } else {
                        FileListPane(
                            files = files,
                            root = root,
                            selectedPath = selected,
                            onSelect = { file ->
                                selected = file.path
                                onSelect?.invoke(file)
                            },
                            modifier = listModifier,
                            backdrop = backdrop,
                        )
                    }
                }
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        list(Modifier.weight(0.42f).fillMaxHeight())
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(colors.borderL1))
                        PreviewPane(
                            path = selected,
                            preview = shown,
                            loaderWired = loader != null,
                            downloader = downloader,
                            showBack = false,
                            onBack = {},
                            onRetry = retry,
                            onSaveFile = onSaveFile,
                            backdrop = backdrop,
                            modifier = Modifier.weight(0.58f).fillMaxHeight(),
                        )
                    }
                } else if (selected == null) {
                    list(Modifier.fillMaxSize())
                } else {
                    PreviewPane(
                        path = selected,
                        preview = shown,
                        loaderWired = loader != null,
                        downloader = downloader,
                        showBack = true,
                        onBack = { selected = null },
                        onRetry = retry,
                        onSaveFile = onSaveFile,
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * This session / Browse, as one segmented capsule.
 *
 * The selected half is a filled shape rather than a tint alone, because a phone in
 * sunlight and a reader who cannot separate two greys both need a shape to read, not
 * a hue. The two halves are equal-width inside one track so the control reads as a
 * single switch instead of two loose words.
 *
 * The track is deliberately *not* glass: it sits directly under the panel's own glass
 * header, and two sheets of the same material stacked on each other read as one thick
 * slab with a seam. The track is the recessed fill the app already uses for grouped
 * controls, with the selected half lifted to `bgLayer1`.
 */
@Composable
private fun FilesModeTabs(
    browse: Boolean,
    onBrowse: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(colors.codeBlock)
            .border(0.5.dp, colors.borderL1, RoundedCornerShape(DshRadius.pill))
            .padding(DshSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.xxs),
    ) {
        ModeTab("This session", selected = !browse, modifier = Modifier.weight(1f)) { onBrowse(false) }
        ModeTab("Browse", selected = browse, modifier = Modifier.weight(1f)) { onBrowse(true) }
    }
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = DshTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(DshRadius.pill))
            .background(if (selected) colors.bgLayer1 else Color.Transparent)
            .clickableNoRipple(onClick = onClick)
            .padding(vertical = DshSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = DshType.labelMedium,
            color = if (selected) colors.labelPrimary else colors.labelTertiary,
            maxLines = 1,
        )
    }
}

/**
 * The workspace tree for one directory: breadcrumb over the level's rows.
 *
 * A directory descends in place, a file selects, and `other` is a row that cannot
 * be opened — the host's `read` refuses it for the same reason. Nothing here
 * pages: `workspaceFiles/list` is a one-shot call with no cursor, so the host's
 * entry cap is the only bound and [ExplorerState.Ready]'s `truncated` flag is
 * reported rather than hidden.
 */
@Composable
private fun ExplorerPane(
    root: String,
    state: ExplorerState,
    badges: Map<String, SessionFile>,
    selectedPath: String?,
    onList: (String) -> Unit,
    onReload: () -> Unit,
    onSelectFile: (String, SessionFile?) -> Unit,
    modifier: Modifier = Modifier,
    tree: WorkspaceTreeState? = null,
    onToggle: ((String) -> Unit)? = null,
    /** The panel's own backdrop, sampled by the header pills. Null paints flat. */
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    var showHidden by remember { mutableStateOf(false) }
    val listing = (state as? ExplorerState.Ready)?.listing
    val current = when (state) {
        is ExplorerState.Ready -> state.listing.path
        is ExplorerState.Loading -> state.path
        is ExplorerState.Failed -> state.path
        ExplorerState.Idle -> ""
    }
    // The level's own view state. Both reset when the level changes — a filter typed
    // for `out/` must not silently hide rows in `src/`, which would read as an empty
    // directory rather than as a filter still being applied.
    var query by rememberSaveable(current) { mutableStateOf("") }
    var order by rememberSaveable(current) { mutableStateOf(ExplorerOrder.NAME) }
    val crumbScroll = rememberScrollState()
    // Following `maxValue` rather than the path: the row's width is only known
    // after layout, and the effect body runs before it — keyed on the path alone
    // it would scroll to the previous row's end and stop short.
    LaunchedEffect(crumbScroll) {
        snapshotFlow { crumbScroll.maxValue }.collect { crumbScroll.scrollTo(it) }
    }
    val entries = remember(listing, badges, showHidden, order, query) {
        listing?.let { WorkspaceExplorer.visibleEntries(it, badges, showHidden, order, query) }
            .orEmpty()
    }
    // The tree's rows, when the parent holds one. The same filter, order and hidden
    // rules apply — the fold takes them as parameters — so the toolbar keeps meaning
    // what it meant: it narrows every open level, not only the one on top.
    val rows = remember(tree, badges, showHidden, order, query) {
        tree?.rows(badges, showHidden, order, query).orEmpty()
    }
    // Whether the *unfiltered* level has rows. The two empty states are different
    // facts — "this directory is empty" and "your filter matched nothing" — and a
    // reader who typed has to be told which one they are looking at.
    val levelHasRows = remember(listing, badges, showHidden) {
        listing?.let { WorkspaceExplorer.visibleEntries(it, badges, showHidden) }.orEmpty()
            .isNotEmpty()
    }
    val clipboard = LocalClipboardManager.current
    var filtering by rememberSaveable(current) { mutableStateOf(query.isNotEmpty()) }
    val atRoot = current.isEmpty()
    val parent = current.substringBeforeLast('/', missingDelimiterValue = "")
    // The path as the host spells it, which is what a tool call or a shell command in
    // this workspace needs. The breadcrumb is shortened for reading; this is the one
    // place the exact string is available.
    val exactPath = if (current.isEmpty()) root else "$root/$current"

    Column(modifier) {
        // The header is the glass: one pill carrying where you are and one carrying
        // what you can do. The old row put Reload, the breadcrumb, Up, Copy path, the
        // sort chip, the filter box and the hidden toggle on four stacked lines, which
        // read as a form rather than as a file browser.
        Column(Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)) {
            FilesGlassHeader(
                backdrop = backdrop,
                crumbs = WorkspaceExplorer.crumbs(current, WorkspaceExplorer.basename(root)),
                current = current,
                onCrumb = onList,
                crumbScroll = crumbScroll,
            )
            Spacer(Modifier.height(DshSpacing.sm))
            FilesToolbar(
                backdrop = backdrop,
                atRoot = atRoot,
                listing = listing != null,
                order = order,
                showHidden = showHidden,
                filtering = filtering,
                onUp = { onList(parent) },
                onReload = onReload,
                onCopyPath = { clipboard.setText(AnnotatedString(exactPath)) },
                onCycleOrder = {
                    // Three orders, as a cycling chip rather than a menu: with three
                    // values and no other option on the row, a popup costs two taps to
                    // change one word.
                    order = when (order) {
                        ExplorerOrder.NAME -> ExplorerOrder.SIZE
                        ExplorerOrder.SIZE -> ExplorerOrder.RECENT
                        ExplorerOrder.RECENT -> ExplorerOrder.NAME
                    }
                },
                onToggleHidden = { showHidden = !showHidden },
                onToggleFilter = { filtering = !filtering },
            )
            // The filter. Only drawn once the level has something to filter: an empty
            // directory with a search box over it reads as a search that failed — and
            // now it is also behind a toggle, so a reader who is only browsing never
            // pays for the row.
            if (filtering && (levelHasRows || query.isNotEmpty())) {
                Spacer(Modifier.height(DshSpacing.sm))
                DshTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Filter this directory",
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 36.dp,
                    verticalPadding = DshSpacing.sm,
                    keyboardCapitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Search,
                    onClear = { query = "" },
                    clearContentDescription = "Clear the filter",
                    shape = RoundedCornerShape(DshRadius.pill),
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state is ExplorerState.Failed -> ExplorerFailure(
                    message = state.message,
                    onRetry = onReload,
                    modifier = Modifier.fillMaxSize(),
                )

                // Loading and Idle both draw the same sentence: there is nothing
                // held for the requested level either way, and the previous
                // level's rows under a new breadcrumb would be a lie.
                listing == null -> ExplorerNote("Loading…", Modifier.fillMaxSize())

                // The two empty states are different facts. "Empty directory" is a
                // statement about the host's answer; "no match" is a statement about
                // the filter, and it names the word that caused it so the reader does
                // not have to work out that they are the reason the list is blank.
                // The tree has rows of its own: an open level can be empty while the
                // tree as a whole is not, and judging "nothing to draw" by the top
                // level alone would blank the list under a subtree that is showing.
                (if (tree != null) rows.isEmpty() else entries.isEmpty()) && levelHasRows ->
                    ExplorerNote("Nothing here matches \"${query.trim()}\"", Modifier.fillMaxSize())

                (if (tree != null) rows.isEmpty() else entries.isEmpty()) ->
                    ExplorerNote("Empty directory", Modifier.fillMaxSize())

                else -> {
                    val listState = rememberLazyListState()
                    DiagLazyList("files:explorer", listState)
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .diagDrag("files:explorer") { listState.diagOffset() },
                        state = listState,
                    ) {
                        if (tree != null) {
                            // The tree draws itself from every level the host has
                            // answered, so one list holds the whole open hierarchy.
                            // Keyed by path rather than by name: the same name appears
                            // at several depths, and a key of "build" would make two
                            // different directories share one row's state.
                            items(rows, key = { it.path }) { row ->
                                ExplorerRow(
                                    row = row,
                                    badge = badges[row.path],
                                    selected = row.path == selectedPath,
                                    onToggle = { onToggle?.invoke(row.path) },
                                    onDescend = { onList(row.path) },
                                    onOpen = { onSelectFile(row.path, badges[row.path]) },
                                )
                            }
                        } else {
                            items(entries, key = { it.name }) { entry ->
                                val childPath = listing.childPath(entry)
                                ExplorerRow(
                                    row = TreeRow(
                                        path = childPath,
                                        entry = entry,
                                        depth = 0,
                                        children = TreeChildren.CLOSED,
                                    ),
                                    badge = badges[childPath],
                                    selected = childPath == selectedPath,
                                    onToggle = null,
                                    onDescend = { onList(childPath) },
                                    onOpen = { onSelectFile(childPath, badges[childPath]) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // The host cut the level at its entry cap; saying so is what stops the
        // missing tail from reading as an empty directory.
        if (listing?.truncated == true) {
            Text(
                text = "Too many entries, showing only some of them.",
                style = DshType.micro,
                color = colors.warn,
                modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xxs),
            )
        }

        // The standing convention, in words. Absence of a mark is a fact about the
        // session, and a reader who has not been told will read it as unknown.
        Text(
            text = "A file with no mark has not been touched by this session.",
            style = DshType.micro,
            color = colors.labelTertiary,
            modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xs),
        )
    }
}

/**
 * The explorer's header: the workspace, then the path inside it, as one glass pill.
 *
 * The path is a *breadcrumb* rather than a string: every segment is a tap that lists
 * that level, which is how a reader climbs out of a deep directory without aiming at a
 * small "Up". It scrolls sideways and follows its own end, because the segment a
 * reader wants is the one furthest right, and on a narrow phone that is exactly the
 * one that runs off the edge.
 *
 * The first segment is the workspace root itself — named by the host, not by this
 * panel — because every level below it is workspace-*relative*: without it the pane
 * could be listing any workspace at all, and the path would begin at a name that
 * means nothing on its own.
 */
@Composable
private fun FilesGlassHeader(
    backdrop: GlassBarBackdrop?,
    crumbs: List<WorkspaceCrumb>,
    current: String,
    onCrumb: (String) -> Unit,
    crumbScroll: ScrollState,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.pill)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            // The lift first, as the bar does: a shadow is drawn outside the pane, and
            // on a light appearance glassLift declines to draw one at all.
            .glassLift(shape)
            .liquidPane(backdrop, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = DshIconVectors.FolderOpenOutline,
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.padding(start = DshSpacing.md, end = DshSpacing.xs).size(16.dp),
        )
        Row(
            Modifier.weight(1f).horizontalScroll(crumbScroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Icon(
                        DshIconVectors.ChevronRightOutline,
                        contentDescription = null,
                        tint = colors.labelCaption,
                        modifier = Modifier.size(14.dp),
                    )
                }
                // The leaf is the level on screen and is drawn as the name of the
                // place; every ancestor is a way back up, so it is the one that reads
                // as a link. The root is always the first segment and is named by the
                // host, not by this panel.
                val leaf = crumb.path == current
                Text(
                    text = crumb.name,
                    style = if (leaf) DshType.labelMedium else DshType.bodySmall,
                    color = if (leaf) colors.labelPrimary else colors.labelSecondary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(DshRadius.sm))
                        .clickableNoRipple { onCrumb(crumb.path) }
                        .padding(horizontal = DshSpacing.xs, vertical = DshSpacing.xxs),
                )
            }
        }
    }
}

/**
 * What you can do to the level, as a row of small glass pills.
 *
 * The controls are the same five the old three rows carried — up, reload, copy path,
 * sort, hidden — plus the filter toggle, which used to be a full-width text field
 * permanently on screen. They are pills rather than bare words because a word on a
 * scrolling page has no edge: a reader cannot tell what the tap target is, and the row
 * reads as prose rather than as controls.
 *
 * Sort cycles instead of opening a menu, and that is deliberate rather than lazy: there
 * are three orders and no other option on this row, so a popup would cost two taps to
 * change one word. The pill says which order is in force, so the state is never hidden.
 */
@Composable
private fun FilesToolbar(
    backdrop: GlassBarBackdrop?,
    atRoot: Boolean,
    listing: Boolean,
    order: ExplorerOrder,
    showHidden: Boolean,
    filtering: Boolean,
    onUp: () -> Unit,
    onReload: () -> Unit,
    onCopyPath: () -> Unit,
    onCycleOrder: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.xs),
    ) {
        // "Up" is a control rather than left to the breadcrumb because the breadcrumb's
        // segments are only as wide as their names, and on a deep path the ancestor a
        // reader wants is the one scrolled off the left edge. It is disabled — not
        // hidden — at the workspace root: the host confines workspaceFiles/list to the
        // session's root, so there is genuinely nowhere above it, and a control that
        // vanishes at one level teaches the reader that it cannot be relied on.
        GlassToolPill(
            backdrop = backdrop,
            icon = DshIconVectors.ChevronUpOutline,
            label = "Up",
            enabled = !atRoot,
            onClick = onUp,
        )
        // The one invalidation. The endpoint has no watch the tree subscribes to, so a
        // file the session has just written appears only after this.
        GlassToolPill(
            backdrop = backdrop,
            icon = DshIconVectors.RefreshOutline,
            label = "Reload",
            enabled = true,
            onClick = onReload,
        )
        GlassToolPill(
            backdrop = backdrop,
            icon = DshIconVectors.CopyOutline,
            label = "Copy path",
            enabled = listing,
            onClick = onCopyPath,
        )
        GlassToolPill(
            backdrop = backdrop,
            icon = Icons.Rounded.Sort,
            label = when (order) {
                ExplorerOrder.NAME -> "Name"
                ExplorerOrder.SIZE -> "Size"
                ExplorerOrder.RECENT -> "Recent"
            },
            enabled = true,
            active = order != ExplorerOrder.NAME,
            onClick = onCycleOrder,
        )
        GlassToolPill(
            backdrop = backdrop,
            icon = DshIconVectors.SlidersTwoOutline,
            label = "Filter",
            enabled = true,
            active = filtering,
            onClick = onToggleFilter,
        )
        GlassToolPill(
            backdrop = backdrop,
            icon = if (showHidden) Icons.Rounded.VisibilityOff else DshIconVectors.InspectOutline,
            label = "Show hidden files",
            enabled = true,
            active = showHidden,
            onClick = onToggleHidden,
        )
    }
}

/**
 * One control on the explorer's toolbar: a small glass pill with a glyph and a word.
 *
 * The disabled state stays *visible* rather than disappearing, for the reason the old
 * text action did: a control that vanishes at one level cannot be relied on. It is
 * drawn in labelCaption and its click is not attached at all, so it cannot be fired by
 * accident and is not reachable as an accessibility action.
 */
@Composable
private fun GlassToolPill(
    backdrop: GlassBarBackdrop?,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    /**
     * The glyph's accessibility label, when the visible word is not enough.
     *
     * Most pills leave it null: the word beside the glyph already names the control,
     * and a second label would make the row read as "Up, up". The back pill is the
     * exception — it says "Files", which names the destination but not the action, and
     * "Back to files" is the sentence a screen reader should say.
     */
    iconDescription: String? = null,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.pill)
    val tint = when {
        !enabled -> colors.labelCaption
        active -> colors.accent
        else -> colors.labelSecondary
    }
    Row(
        modifier
            .height(30.dp)
            .glassLift(shape)
            .liquidPane(backdrop, shape)
            .then(if (enabled) Modifier.clickableNoRipple(onClick = onClick) else Modifier)
            .padding(horizontal = DshSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(text = label, style = DshType.bodySmall, color = tint, maxLines = 1)
    }
}

/** One explorer row: kind icon, name, size, and the session's marks. */
@Composable
private fun ExplorerRow(
    row: TreeRow,
    badge: SessionFile?,
    selected: Boolean,
    onToggle: (() -> Unit)?,
    onDescend: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = DshTheme.colors
    val entry = row.entry
    val directory = entry.kind == WorkspaceEntryKind.DIRECTORY
    val openable = entry.kind != WorkspaceEntryKind.OTHER
    // A directory expands in place when the parent holds a tree; without one it
    // still descends, which is the behaviour every existing caller has.
    val expands = directory && onToggle != null
    val open = row.children != TreeChildren.CLOSED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DshRadius.md))
            .background(if (selected) colors.sidebarItemActive else colors.bgBase)
            .clickableNoRipple(enabled = openable) {
                // Bound to a local first: `expands` already implies a non-null
                // `onToggle`, but the compiler cannot narrow a function-typed
                // parameter through a captured val.
                val toggle = onToggle
                when {
                    expands && toggle != null -> toggle()
                    directory -> onDescend()
                    else -> onOpen()
                }
            }
            .padding(
                // The indent is the tree's only statement about hierarchy, so it is
                // carried by the row rather than inferred from the path: a filtered
                // level can hide an ancestor, and a path-derived indent would then
                // step in by two for a row whose parent is not on screen.
                start = DshSpacing.lg + (DshSpacing.lg * row.depth),
                end = DshSpacing.lg,
                top = DshSpacing.sm,
                bottom = DshSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.md),
    ) {
        // The disclosure triangle: a real control rather than decoration, because a
        // reader aiming at a deep row aims at the icon, and making them hit the name
        // instead is the difference between a tree and a list of folders.
        if (expands) {
            Icon(
                imageVector = if (open) DshIconVectors.ChevronDownOutline else DshIconVectors.ChevronRightOutline,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = colors.labelTertiary,
                modifier = Modifier.size(14.dp),
            )
        } else if (row.depth > 0) {
            // A spacer where a file's triangle would be, so a file's icon lines up
            // with its sibling directories' icons rather than with their triangles.
            Spacer(Modifier.size(14.dp))
        }
        Icon(
            imageVector = if (directory) DshIconVectors.FolderOpenOutline else fileIcon(entry.name),
            contentDescription = null,
            tint = if (openable) colors.labelTertiary else colors.labelCaption,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = entry.name,
            style = DshType.labelMedium,
            color = if (openable && !entry.hidden) colors.labelPrimary else colors.labelTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Bound to a local first: `entry.size` is a public API property from
        // :core:model, and Kotlin will not smart-cast across a module boundary.
        val entrySize = entry.size
        if (entrySize != null) {
            Text(
                text = formatSize(entrySize),
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                maxLines = 1,
            )
        }
        badge?.let { TouchBadges(it) }
        // The trailing seat says what is happening with this directory's children.
        // A chevron when it can only descend; a spinner while a level is in flight;
        // the warning tint when the host refused it. An open directory shows nothing
        // here — its children are directly below, which is the statement.
        when {
            row.children == TreeChildren.LOADING -> CircularProgressIndicator(
                color = colors.labelCaption,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
            )

            row.children == TreeChildren.FAILED -> Icon(
                DshIconVectors.WarningTriangleOutline,
                contentDescription = row.failure ?: "Could not list this directory",
                tint = colors.warn,
                modifier = Modifier.size(14.dp),
            )

            directory && !expands -> Icon(
                DshIconVectors.ChevronRightOutline,
                contentDescription = null,
                tint = colors.labelCaption,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ExplorerNote(text: String, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Box(modifier.padding(DshSpacing.xxl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The host's own wording for a refused listing, plus the way to ask again. */
@Composable
private fun ExplorerFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Column(
        modifier.padding(DshSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
    ) {
        Text(
            text = message,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.Center,
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

/** The mark set for one touched file, shared by both lists so they cannot drift. */
@Composable
private fun TouchBadges(file: SessionFile, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Text(
        text = file.touches.joinToString(" · ") { touchLabel(it) },
        style = DshType.bodySmall,
        color = if (file.modified) colors.link else colors.labelTertiary,
        maxLines = 1,
        modifier = modifier,
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** Nothing touched yet — the panel's own empty state, not the web's "Empty directory". */
@Composable
private fun FilesEmptyState() {
    val colors = DshTheme.colors
    Column(
        Modifier.padding(DshSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
    ) {
        Icon(
            imageVector = DshIconVectors.DeliverDoc,
            contentDescription = null,
            tint = colors.labelCaption,
            modifier = Modifier.size(28.dp),
        )
        Text("No files yet", style = DshType.labelMedium, color = colors.labelSecondary)
        Text(
            text = "Files this session reads, writes, edits, or presents will appear here.",
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The touch list: a glass header pill over a flat, first-seen file order. */
@Composable
private fun FileListPane(
    files: List<SessionFile>,
    root: String?,
    selectedPath: String?,
    onSelect: (SessionFile) -> Unit,
    modifier: Modifier = Modifier,
    /** The panel's backdrop, sampled by the header pill. Null paints flat. */
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    Column(modifier) {
        val shape = RoundedCornerShape(DshRadius.pill)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)
                .height(44.dp)
                .glassLift(shape)
                .liquidPane(backdrop, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = DshIconVectors.DeliverDoc,
                contentDescription = null,
                tint = colors.labelTertiary,
                modifier = Modifier.padding(start = DshSpacing.md, end = DshSpacing.xs).size(16.dp),
            )
            if (root != null) {
                PathCrumb(root, Modifier.weight(1f).padding(end = DshSpacing.sm))
            } else {
                Text(
                    text = "Files",
                    style = DshType.labelMedium,
                    color = colors.labelPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            // The count is a fact about *this list*, and it is the one thing the header
            // can say that the rows cannot: how many there are before you scroll.
            Text(
                text = if (files.size == 1) "1 file" else "${files.size} files",
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                maxLines = 1,
                modifier = Modifier.padding(end = DshSpacing.md),
            )
        }
        val listState = rememberLazyListState()
        DiagLazyList("files:tree", listState)
        LazyColumn(
            Modifier
                .fillMaxSize()
                .diagDrag("files:tree") { listState.diagOffset() },
            state = listState,
        ) {
            items(files, key = { it.path }) { file ->
                FileRow(file, selected = file.path == selectedPath, onSelect = { onSelect(file) })
            }
        }
    }
}
/** One touched file: name, touch badges, and the path's directory tail. */
@Composable
private fun FileRow(file: SessionFile, selected: Boolean, onSelect: () -> Unit) {
    val colors = DshTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DshRadius.md))
            .background(if (selected) colors.sidebarItemActive else colors.bgBase)
            .clickableNoRipple(onClick = onSelect)
            .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DshSpacing.md),
    ) {
        Icon(
            imageVector = fileIcon(file.path),
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = file.basename,
                style = DshType.labelMedium,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shortenPath(directoryOf(file.path)),
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TouchBadges(file)
    }
}

/**
 * The text preview: a path header over a wrapped, line-numbered monospace body — or,
 * for a Markdown document, over its rendering.
 *
 * The web's code/plain-text renderers load with `text-pages` and wrap by
 * default, which is why long lines wrap here instead of scrolling sideways.
 * A page the host cut short says so above the body; the panel never scrolls
 * horizontally, so a minified or generated line still reads down the page.
 *
 * Three things are drawn here and the header says which:
 *
 *  - a **Markdown rendering**, which replaces the source and therefore always has a
 *    Source toggle beside it;
 *  - the **source**, with code coloured when the extension names a language this app
 *    has a grammar for — and left plain when it does not, because colouring an unknown
 *    file would claim it is code;
 *  - a **picture**, with the SVG toggle for the one format that is both.
 *
 * Whatever is on screen, a window the host cut short is announced above it. Without
 * that, a rendering of the first two thousand lines would read as the whole document.
 *
 * This pane is mode-independent on purpose. It is composed below the mode tabs, over
 * whichever list selected the file, because a rendering, its Source toggle and its
 * window banner are claims about *the file* — and a file reached through Browse is the
 * same file. The one asymmetry Browse brings is spelling: the transcript's key is
 * preferred when the row carries a badge ([readKey] in the panel above), while the read
 * itself still travels under the path on screen, so the preview cannot be showing one
 * file's cache while it names another.
 */
@Composable
private fun PreviewPane(
    path: String?,
    preview: FilePreview?,
    /** True when a loader is wired: an empty first frame reads as "Reading…". */
    loaderWired: Boolean,
    /** On-demand whole-file read; null draws no Download control at all. */
    downloader: FileDownloader?,
    showBack: Boolean,
    onBack: () -> Unit,
    /**
     * Drops the memoized read and asks again. It takes no path because the
     * panel's map is keyed by the transcript's spelling of the selection, which
     * is not always the path on screen — the caller already holds that key.
     */
    onRetry: () -> Unit,
    /** Writes the edited text back; null draws no Edit control at all. */
    onSaveFile: ((path: String, text: String) -> Unit)? = null,
    /** The panel's backdrop, sampled by the preview's own header pills. */
    backdrop: GlassBarBackdrop? = null,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val ready = preview as? FilePreview.Ready
    val picture = preview as? FilePreview.Image
    // Bound to a local before any lambda captures it: a nullable parameter of function
    // type does not smart-cast once a composable lambda holds it.
    val save = onSaveFile
    // SVG opens as the drawing; the toggle is the way to the markup. Defaulted per
    // *path* so switching files does not carry the last file's choice over.
    var svgAsImage by rememberSaveable(path) { mutableStateOf(true) }
    // Markdown opens rendered — that is the request this exists to answer — and the
    // toggle is the way back to the source, which is not an afterthought: the file
    // being previewed is often a document the reader is debugging.
    var markdownRendered by rememberSaveable(path) { mutableStateOf(true) }
    val isSvg = ready != null && previewFormatOf(path.orEmpty()) == PreviewFormat.SVG
    val isMarkdown = ready != null && isMarkdownPath(path.orEmpty())
    val language = ready?.lang ?: previewLanguageOf(path.orEmpty())
    // Nothing about a fetch that succeeds, and everything about one that does not: the
    // control's own label is one word, so the reason gets a line here.
    var downloadMessage by remember(path) { mutableStateOf<String?>(null) }

    // ---- editing -------------------------------------------------------------
    // The draft is keyed on the path *and* on the text the preview arrived with: a
    // write that lands, or a re-read that brings a newer page, must not leave an
    // editor holding the version it opened with. Discarding drops it back to the
    // host's text, so re-entering an untouched file always starts from what it said.
    var editing by remember(path) { mutableStateOf(false) }
    var draft by remember(path, ready?.text) { mutableStateOf(ready?.text.orEmpty()) }
    var confirmingSave by remember(path) { mutableStateOf(false) }
    val dirty = editing && ready != null && draft != ready.text
    // Only a text page the host delivered *in full* is editable. A windowed or
    // truncated page is a part of the file: saving it would write the first two
    // thousand lines back and silently delete the rest, which is the one failure a
    // confirm dialog cannot rescue. A picture and a failed read are not text at all.
    val editable = save != null && ready != null && !ready.windowed && !ready.truncated

    // A save writes the user's real workspace, so it is confirmed once, in the app's
    // own dialog vocabulary rather than a second Material one. The panel then leaves
    // edit mode and stops speaking: it does not wait for the write and does not claim
    // it landed, because the parent's reload is what decides what the file now says.
    if (confirmingSave && path != null && save != null) {
        ConfirmDialogHost(
            show = true,
            visuals = ConfirmDialogVisuals(
                title = "Save changes?",
                content = "This writes the edited text back to " +
                    "${shortenPath(path)} on the machine running DSH. " +
                    "Anything written to it since this preview was read is replaced.",
                confirm = "Save",
                confirmTone = ConfirmTone.Warning,
                dismiss = "Cancel",
            ),
            onConfirm = {
                confirmingSave = false
                editing = false
                save(path, draft)
            },
            onDismiss = { confirmingSave = false },
        )
    }

    Column(modifier) {
        // The preview's own header: the file as a glass pill, then its controls.
        Column(Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.sm)) {
            PreviewGlassHeader(path = path, backdrop = backdrop)
            Spacer(Modifier.height(DshSpacing.sm))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DshSpacing.xs),
            ) {
                if (showBack && !editing) {
                    GlassToolPill(
                        backdrop = backdrop,
                        icon = DshIconVectors.ChevronLeftOutline,
                        label = "Files",
                        enabled = true,
                        onClick = onBack,
                        iconDescription = "Back to files",
                    )
                }
                // SVG is both a drawing and a document, and which one the reader wants
                // depends on what they are doing — look at the icon, or read its path.
                // The choice sits here rather than behind a second file row, and Markdown
                // is the same shape of choice between a document and its source.
                if (isSvg && !editing) {
                    GlassToolPill(
                        backdrop = backdrop,
                        icon = Icons.Rounded.Image,
                        label = if (svgAsImage) "Source" else "Image",
                        enabled = true,
                        onClick = { svgAsImage = !svgAsImage },
                    )
                }
                if (isMarkdown && !editing) {
                    GlassToolPill(
                        backdrop = backdrop,
                        icon = DshIconVectors.BrowseOutline,
                        label = if (markdownRendered) "Source" else "Rendered",
                        enabled = true,
                        onClick = { markdownRendered = !markdownRendered },
                    )
                }
                // A claim, drawn as a chip: what this app thinks the file is. It is shown
                // whether or not anything is coloured, so a misnamed file's reader can see
                // the claim the panel is making and check it against the source.
                language?.let { lang ->
                    Text(lang, style = DshType.bodySmall, color = colors.labelTertiary, maxLines = 1)
                }
                if (ready != null && ready.lines > 0 && !editing) {
                    CopyPreviewButton(ready.text)
                }
                // The edit toggle exists only where a save channel does, and only for a
                // page the host delivered whole — see `editable`.
                if (editable && !editing) {
                    GlassToolPill(
                        backdrop = backdrop,
                        icon = DshIconVectors.EditOutline,
                        label = "Edit",
                        enabled = true,
                        onClick = { draft = ready?.text.orEmpty(); editing = true },
                    )
                }
                // Offered for whatever is selected, including a file whose *preview*
                // failed: a PDF or a binary is exactly what the text read refuses, and a
                // download is the only way to get it onto the phone. It reads and writes
                // nothing until it is tapped, and every download it starts dies with this
                // composition — the panel's own close is therefore also its cancel.
                if (path != null && downloader != null && !editing) {
                    FileDownloadControl(
                        path = path,
                        downloader = downloader,
                        onMessage = { downloadMessage = it },
                    )
                }
            }
        }

        downloadMessage?.let { message ->
            Text(
                text = message,
                style = DshType.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xs),
            )
        }

        when {
            path == null -> PreviewNote("Select a file to preview.", Modifier.weight(1f).fillMaxWidth())
            preview is FilePreview.Loading -> PreviewNote("Reading…", Modifier.weight(1f).fillMaxWidth())
            // The host's own wording, verbatim: an unsupported type and a missing
            // file read the same way here, as they do in the web's viewer.
            preview is FilePreview.Unavailable -> PreviewFailure(
                preview.message.ifBlank { "Could not read this file." },
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            // A picture, which arrived as bytes because the text read refuses anything
            // that is not UTF-8. Decoding is the pane's job: the model layer carries
            // bytes, and this is where the platform's decoder is.
            picture != null -> PicturePreview(
                picture = picture,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            ready == null -> PreviewNote(
                if (loaderWired) {
                    // The fetch effect has not run its first frame yet.
                    "Reading…"
                } else {
                    "Nothing read from this file in this session."
                },
                Modifier.weight(1f).fillMaxWidth(),
            )

            // The editor replaces every reading of the file, including the rendered one:
            // a reader who is changing the source wants the source, not the page.
            // The null test is written out rather than inherited from the branches above:
            // a smart cast does not cross into a non-inline composable lambda, and this
            // body reads the page from inside one.
            editing && ready != null -> {
                // Bound to a non-null local *here*, outside the content lambda: the
                // page is read from inside two lambdas below, and a local val is the
                // one thing a smart cast survives that far.
                val page = ready
                Column(Modifier.fillMaxSize()) {
                    WindowBanner(page, rendered = false)
                    FileEditor(
                        text = draft,
                        onTextChange = { draft = it },
                        language = language,
                        dirty = dirty,
                        onCancel = {
                            draft = page.text
                            editing = false
                        },
                        onSave = { confirmingSave = true },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        backdrop = backdrop,
                    )
                }
            }

            // Nothing to show and nothing being written: the file is genuinely empty.
            // It is checked *after* the editor so an empty file can still be filled in,
            // which is the one case where an editor over nothing is the point.
            ready.lines == 0 -> PreviewNote("This file is empty.", Modifier.weight(1f).fillMaxWidth())

            isSvg && svgAsImage -> SvgPreview(
                markup = ready.text,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            isMarkdown && markdownRendered -> Column(Modifier.fillMaxSize()) {
                WindowBanner(ready, rendered = true)
                MarkdownPreview(ready.text, Modifier.weight(1f).fillMaxWidth())
            }

            else -> Column(Modifier.fillMaxSize()) {
                WindowBanner(ready, rendered = false)
                val previewState = rememberLazyListState()
                DiagLazyList("files:preview", previewState)
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .diagDrag("files:preview") { previewState.diagOffset() },
                    state = previewState,
                ) {
                    val highlight = language != null && !isMarkdown
                    items(ready.numbered, key = { it.number }) { line ->
                        PreviewLineRow(line, highlight, colors)
                    }
                }
            }
        }
    }
}

/**
 * The file the preview is showing, as one glass pill.
 *
 * Directory in tertiary ink and the final segment in full ink, which is the web's own
 * path row: the eye lands on the name and the directory is there to be checked. It is
 * a *reading* rather than a breadcrumb — the preview names one file, and there is no
 * level to climb to from here, because the list behind it is what moves.
 */
@Composable
private fun PreviewGlassHeader(
    path: String?,
    backdrop: GlassBarBackdrop?,
    modifier: Modifier = Modifier,
) {
    val colors = DshTheme.colors
    val shape = RoundedCornerShape(DshRadius.pill)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .glassLift(shape)
            .liquidPane(backdrop, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (path == null) DshIconVectors.DeliverDoc else fileIcon(path),
            contentDescription = null,
            tint = colors.labelTertiary,
            modifier = Modifier.padding(start = DshSpacing.md, end = DshSpacing.xs).size(16.dp),
        )
        if (path != null) {
            PathCrumb(path, Modifier.weight(1f).padding(end = DshSpacing.md))
        } else {
            Text(
                text = "No file selected",
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                modifier = Modifier.weight(1f).padding(end = DshSpacing.md),
            )
        }
    }
}

/**
 * The editable half of a text preview: a monospace field over the whole pane.
 *
 * Monospace rather than the prose body, because what is being edited is a file: the
 * column alignment a reader has been looking at in the preview is the thing that makes
 * a diff readable, and proportional text would lose it the moment the caret lands.
 * Wrapping stays on for the same reason the preview wraps — the panel never scrolls
 * sideways, and a minified line is not made editable by clipping it.
 *
 * The two controls say what they will do rather than "OK"/"Cancel". Save is disabled
 * while nothing has changed, so a tap that would write the same bytes back is not
 * offered; Discard throws away the *draft* and never touches the file.
 */
@Composable
private fun FileEditor(
    text: String,
    onTextChange: (String) -> Unit,
    language: String?,
    dirty: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBarBackdrop? = null,
) {
    val colors = DshTheme.colors
    val field = remember { FocusRequester() }
    // The caret goes into the file on the first frame: the reader tapped Edit, and an
    // editor that opens without focus makes them tap again before they can type.
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DshSpacing.sm),
        ) {
            Text(
                text = if (dirty) "Editing · unsaved changes" else "Editing",
                style = DshType.micro,
                color = if (dirty) colors.warn else colors.labelTertiary,
                modifier = Modifier.weight(1f),
            )
            language?.let { lang ->
                Text(lang, style = DshType.micro, color = colors.labelTertiary, maxLines = 1)
            }
            GlassToolPill(
                backdrop = backdrop,
                icon = DshIconVectors.CloseOutline,
                label = "Discard",
                enabled = true,
                onClick = onCancel,
            )
            GlassToolPill(
                backdrop = backdrop,
                icon = Icons.Rounded.Save,
                label = "Save",
                enabled = dirty,
                active = dirty,
                onClick = onSave,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = DshSpacing.lg)
                .clip(RoundedCornerShape(DshRadius.card))
                .background(colors.codeBlock)
                .border(0.5.dp, colors.borderL1, RoundedCornerShape(DshRadius.card)),
        ) {
            // The scroll belongs to a container around the field, not to the field:
            // a BasicTextField given verticalScroll grows to its content and then has
            // nothing left to scroll, which leaves a long file unable to reach its
            // own tail. The inner box is the viewport; the field is its content.
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(DshSpacing.md),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = DshType.codeSmall.copy(color = colors.labelPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    // No autocorrect and no sentence capitalisation: this is source,
                    // not prose, and an IME that capitalises the first letter of a line
                    // or rewrites an identifier is editing the file behind the reader.
                    // The keyboard *type* stays Text rather than Ascii, because a file
                    // may hold CJK prose and an ASCII keyboard would hide half of it.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(field),
                )
            }
        }
    }
}

/**
 * How much of the file the reader is looking at, when it is not all of it.
 *
 * The transcript knows the file's total length; a workspace page only knows that it
 * stopped short of EOF. [rendered] adds the one thing a rendered document cannot say
 * for itself: that what is on screen is a rendering *of the window*, not of the file.
 *
 * The sentence is about the *page the host sent*, which is why it is true in both
 * modes: a file reached through Browse carries a workspace-relative path and a page the
 * host capped identically, and the cap is the host's, not the list's. What Browse does
 * change is that the panel cannot say how many lines this session has read, so
 * `ready.truncated` — the host's own `eof` — is the only half that can be stated, and
 * the banner says exactly that rather than inventing a total.
 */
@Composable
private fun WindowBanner(ready: FilePreview.Ready, rendered: Boolean) {
    val colors = DshTheme.colors
    val window = when {
        ready.windowed -> "Showing ${ready.lines} of ${ready.totalLines} lines"
        ready.truncated -> "Showing the first ${ready.lines} lines"
        else -> null
    } ?: return
    Text(
        text = if (rendered) "$window · the rest of this document is not shown" else window,
        style = DshType.bodySmall,
        color = colors.labelTertiary,
        modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xs),
    )
}

/**
 * The rendered document, in the panel's own scroller.
 *
 * `MarkdownText` is the same renderer the transcript uses, so a document reads the same
 * whether it is a message or a file — and it is the reason this panel needs no Markdown
 * dependency: the subset, and its streaming tolerance, are already here. Its code blocks
 * scroll sideways on their own; the document scrolls vertically here.
 */
@Composable
private fun MarkdownPreview(text: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    DiagScrollColumn("files:markdown", scroll)
    Column(
        modifier
            .verticalScroll(scroll)
            .diagDrag("files:markdown") { scroll.value }
            .padding(horizontal = DshSpacing.lg, vertical = DshSpacing.md),
    ) {
        MarkdownText(text)
    }
}

/**
 * One numbered line, wrapping rather than truncating (wrap is the web default).
 *
 * [highlight] is decided once for the whole page and passed down, so a row pays for a
 * composition-local read only when the theme changes. The colouring never touches the
 * text: `highlightCodeLine` returns the line split into spans, and their concatenation
 * is the line exactly as it arrived.
 */
@Composable
private fun PreviewLineRow(line: PreviewLine, highlight: Boolean, colors: DshColors) {
    Row(Modifier.fillMaxWidth().padding(horizontal = DshSpacing.lg, vertical = 1.dp)) {
        Text(
            text = line.number.toString(),
            style = DshType.codeSmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(40.dp).padding(end = DshSpacing.md),
        )
        val body = remember(line.text, highlight, colors) {
            when {
                line.text.isEmpty() -> AnnotatedString(" ")
                highlight -> highlightCodeLine(line.text, colors)
                else -> AnnotatedString(line.text)
            }
        }
        Text(
            text = body,
            style = DshType.codeSmall,
            color = colors.labelPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreviewNote(text: String, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Box(modifier.padding(DshSpacing.xxl), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A failed read: the host's own wording, plus a way out.
 *
 * The result is cached so re-selecting does not refetch, which would otherwise
 * strand a transient host failure on screen for the panel's whole life.
 */
@Composable
private fun PreviewFailure(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    Column(
        modifier.padding(DshSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DshSpacing.sm),
    ) {
        Text(
            text = message,
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            textAlign = TextAlign.Center,
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

/** Directory part in tertiary ink, final segment in full ink — the web's path row. */
@Composable
private fun PathCrumb(path: String, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    val home = shortenPath(path)
    val at = maxOf(home.lastIndexOf('/'), home.lastIndexOf('\\'))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (at > 0) {
            Text(
                text = home.substring(0, at + 1),
                style = DshType.bodySmall,
                color = colors.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            text = home.substring(at + 1),
            style = DshType.bodySmall,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Same 1000ms "Copy"/"Copied" swap as the other transcript cards. */
@Composable
private fun CopyPreviewButton(text: String) {
    val colors = DshTheme.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1000)
            copied = false
        }
    }
    Text(
        text = if (copied) "Copied" else "Copy",
        style = DshType.bodySmall,
        color = colors.labelSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(DshRadius.sm))
            .clickableNoRipple {
                if (copied) return@clickableNoRipple
                clipboard.setText(AnnotatedString(text))
                copied = true
            }
            .padding(DshSpacing.xs),
    )
}

private fun touchLabel(touch: FileTouch): String = when (touch) {
    FileTouch.READ -> "Read"
    FileTouch.WRITTEN -> "Write"
    FileTouch.EDITED -> "Edit"
    FileTouch.PRESENTED -> "Presented"
}

private fun directoryOf(path: String): String {
    val at = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    return if (at <= 0) path else path.substring(0, at)
}

/**
 * The row's icon, from the same [previewFormatOf] that decides how it is read.
 *
 * It used to keep its own two extension lists, which is how a file could wear an
 * image icon and still be fetched as text: `heic` and `avif` were "images" to the
 * icon and unknown to the reader. One table, one answer.
 */
private fun fileIcon(path: String): ImageVector = when (previewFormatOf(path)) {
    PreviewFormat.TEXT -> DshIconVectors.BrowseOutline
    else -> Icons.Rounded.Image
}

/**
 * A raster image, decoded off the main thread.
 *
 * `BitmapFactory` is given the bytes as they arrived and left to subsample nothing:
 * the window the loader fetches is already bounded, and a preview that is a little
 * large is scrollable, whereas one decoded down to a thumbnail is not readable. A
 * decode that fails says so — a misnamed file is previewed as what its extension
 * claims, and this is where that shows up rather than as a blank pane.
 */
@Composable
private fun PicturePreview(picture: FilePreview.Image, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    val bitmap by produceState<ImageBitmap?>(initialValue = null, picture.bytes) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.BitmapFactory
                    .decodeByteArray(picture.bytes, 0, picture.bytes.size)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(modifier) {
        Text(
            text = captionFor(picture, bitmap),
            style = DshType.bodySmall,
            color = colors.labelTertiary,
            modifier = Modifier.padding(horizontal = DshSpacing.lg, vertical = DshSpacing.xs),
        )
        val decoded = bitmap
        if (decoded == null) {
            PreviewNote("Could not decode this image.", Modifier.weight(1f).fillMaxWidth())
        } else {
            val imageScroll = rememberScrollState()
            DiagScrollColumn("files:image", imageScroll)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(imageScroll)
                    .diagDrag("files:image") { imageScroll.value }
                    .padding(DshSpacing.md),
            ) {
                Image(
                    bitmap = decoded,
                    contentDescription = picture.absolutePath?.substringAfterLast('/'),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The line above a picture: what it is, how big it is, and how big it draws. */
private fun captionFor(picture: FilePreview.Image, bitmap: ImageBitmap?): String {
    val parts = mutableListOf(picture.format.name.lowercase().replace("jpeg", "jpeg"))
    bitmap?.let { parts += "${it.width}×${it.height}" }
    picture.sizeBytes?.let { parts += formatBytes(it) }
    return parts.joinToString(" · ")
}

private fun formatBytes(bytes: Int): String = formatSize(bytes.toLong())

/**
 * The drawing half of an SVG.
 *
 * Rendered at the pane's own width and left to scroll vertically, because an SVG has
 * no intrinsic resolution to preserve: `AndroidSVG` scales the document, so asking
 * for the pane's width keeps strokes crisp at any zoom the platform applies. The
 * paper is the panel's own background — an SVG's transparent areas are the page's,
 * exactly as in the web viewer, and filling it any other colour would invent a
 * background the document does not have.
 */
@Composable
private fun SvgPreview(markup: String, modifier: Modifier = Modifier) {
    val colors = DshTheme.colors
    val paper = colors.bgBase
    val bitmap by produceState<ImageBitmap?>(initialValue = null, markup) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val svg = SVG.getFromString(markup)
                val width = 1024f
                val ratio = runCatching { svg.documentHeight / svg.documentWidth }
                    .getOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?: 1f
                val height = (width * ratio).coerceIn(32f, 8192f)
                val output = android.graphics.Bitmap.createBitmap(
                    width.toInt(),
                    height.toInt(),
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                output.eraseColor(paper.toArgb())
                val canvas = android.graphics.Canvas(output)
                svg.setDocumentWidth(width)
                svg.setDocumentHeight(height)
                svg.renderToCanvas(canvas)
                output.asImageBitmap()
            }.getOrNull()
        }
    }
    val decoded = bitmap
    if (decoded == null) {
        PreviewNote("Could not render this SVG — switch to Source to read it.", modifier)
        return
    }
    val svgScroll = rememberScrollState()
    DiagScrollColumn("files:svg", svgScroll)
    Column(
        modifier
            .verticalScroll(svgScroll)
            .diagDrag("files:svg") { svgScroll.value }
            .padding(DshSpacing.md),
    ) {
        Image(
            bitmap = decoded,
            contentDescription = "SVG drawing",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

