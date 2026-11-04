package uk.xa0.dsh.model

/**
 * One file's preview content, whatever answered for it.
 *
 * Two host surfaces can answer and they disagree on shape: a transcript read
 * carries numbered `meta.lines` objects, while `workspaceFiles/read` answers one
 * raw page — `{absolutePath, version, bytes, offset, text, lines, eof}`, where
 * `lines` is a *count*, not a list. Both land here so the Files panel renders one
 * vocabulary, and it keeps the web's resolution order: the session resource (the
 * durable transcript window, no RPC) is the first choice and the workspace read
 * is the fallback.
 */
sealed interface FilePreview {

    /** A workspace read is in flight and nothing is held for the path yet. */
    data object Loading : FilePreview

    /**
     * A text page.
     *
     * [text], [lines], [truncated], [bytes] and [absolutePath] are the
     * `workspaceFiles/read` reply, field for field: the page's lines joined by
     * `\n`, how many it holds (`0` when the offset lies past the file's last
     * line), whether the page stops short of `eof`, the reported size, and the
     * host's resolved path. [bytes] and [absolutePath] are null when the host
     * omitted them.
     *
     * [offset], [totalLines] and [lang] are app-side additions that only the
     * transcript source fills: a `read` result knows which line its window
     * started at and how long the whole file is, which the paged endpoint does
     * not report.
     */
    data class Ready(
        val text: String,
        val lines: Int,
        val truncated: Boolean,
        val bytes: Int? = null,
        val absolutePath: String? = null,
        val offset: Int = 1,
        val totalLines: Int? = null,
        val lang: String? = null,
    ) : FilePreview {

        /** [text] split back into the file's own numbered lines; computed once. */
        val numbered: List<PreviewLine> =
            if (lines <= 0) {
                emptyList()
            } else {
                text.split('\n').mapIndexed { index, line -> PreviewLine(offset + index, line) }
            }

        /** Fewer lines than the file's reported total: the banner states the window. */
        val windowed: Boolean get() = totalLines != null && offset + lines - 1 < totalLines
    }

    /** The host refused the read; [message] is its own user-readable wording. */
    data class Unavailable(val message: String) : FilePreview

    /**
     * A picture: the file's own bytes, undecoded.
     *
     * Bytes rather than a bitmap, because decoding belongs to the platform and this
     * model layer is pure Kotlin — the pane decodes off the main thread and holds the
     * result for as long as the path is selected. [sizeBytes] is the host's reported
     * file size, which is what the caption shows; it is not necessarily
     * `bytes.size`, since only a bounded window is ever fetched.
     */
    data class Image(
        val bytes: ByteArray,
        val format: PreviewFormat,
        val sizeBytes: Int? = null,
        val absolutePath: String? = null,
    ) : FilePreview {

        override fun equals(other: Any?): Boolean =
            other is Image && other.format == format && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * format.hashCode() + bytes.contentHashCode()
    }
}

/** What a path is, as far as previewing it goes. */
enum class PreviewFormat {
    TEXT,
    PNG,
    JPEG,
    WEBP,
    GIF,
    BMP,
    ICO,
    /**
     * HEIF/AVIF decode only from API 28 and 30 respectively, above this app's
     * `minSdk` of 26 — where the decoder returns nothing and the pane says the image
     * could not be decoded. That is the honest outcome: reading the *bytes* as text
     * would be worse than saying so.
     */
    HEIC,
    AVIF,
    SVG,
    ;

    val isImage: Boolean get() = this != TEXT
}

/**
 * The format a path names, by extension.
 *
 * Extension rather than sniffing the bytes, which is what the web's viewer does and
 * what makes the *read* choice possible at all: a picture has to be fetched as bytes
 * (`workspaceFiles/readBytes`) and a document as text, and that decision has to be
 * made before the first byte arrives. The cost is that a misnamed file is previewed
 * as what it claims to be — the panel says which it chose, and a decode that fails
 * says so rather than showing nothing.
 *
 * Case-insensitive and query-less, because both occur: a `.PNG` off a camera and a
 * `?raw=1` tail off a URL a session wrote.
 */
fun previewFormatOf(path: String): PreviewFormat {
    val extension = extensionOf(previewNameOf(path))
    return when (extension) {
        "png", "apng" -> PreviewFormat.PNG
        "jpg", "jpeg", "jpe", "jfif" -> PreviewFormat.JPEG
        "webp" -> PreviewFormat.WEBP
        "gif" -> PreviewFormat.GIF
        "bmp" -> PreviewFormat.BMP
        "ico" -> PreviewFormat.ICO
        "heic", "heif" -> PreviewFormat.HEIC
        "avif" -> PreviewFormat.AVIF
        "svg" -> PreviewFormat.SVG
        else -> PreviewFormat.TEXT
    }
}

/** The file name alone, query and fragment stripped; the one name both tables read. */
private fun previewNameOf(path: String): String =
    path.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').substringBefore('#')

/** The lower-cased suffix after the last dot; empty for a name with no dot at all. */
private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

/**
 * The language id a path names, by extension.
 *
 * This is the read card's table (a port of the web's `read-render.ts`), moved here so
 * the read card and the Files panel cannot answer differently about what a `.kt` file
 * is. It is a *claim*, not a fact: nothing here looks inside the file, so a `.py` that
 * holds a shell script is labelled `py`. That is why the claim is drawn as a chip
 * beside the content rather than used to decide what the content means, and why the
 * panel offers the source for any file it renders.
 *
 * A dotfile and an unknown extension both answer `null` — no claim at all, which is
 * what keeps a `.log` or an extensionless file from being coloured as code.
 */
fun previewLanguageOf(path: String): String? {
    val name = previewNameOf(path)
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return null
    return LANGUAGE_BY_EXTENSION[name.substring(dot + 1).lowercase()]
}

/**
 * True when the panel will *render* this path rather than only colour it.
 *
 * Deliberately three extensions and no sniffing of the content: a document whose first
 * line happens to be `#` is not a promise that the rest of it is Markdown, and a
 * renderer that guesses wrong reflows prose the author laid out on purpose. The reader
 * always has the source one tap away, and the toggle names it.
 *
 * `.mdx` is not here: its body is Markdown *plus* JSX, and rendering half of it as
 * prose would be a claim the file does not support.
 */
fun isMarkdownPath(path: String): Boolean =
    extensionOf(previewNameOf(path)) in MARKDOWN_EXTENSIONS

/** The extensions whose whole content is Markdown. */
private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown")

/**
 * Extension to language id, verbatim from the read card's `LANG_BY_EXTENSION`, which
 * is itself the web's `read-render.ts` table, with one addition: `kts`, the Kotlin
 * script suffix (`build.gradle.kts` is a Kotlin file by any reading, and it is this
 * project's own build file). The read card picks the addition up too, since it now asks
 * this table rather than keeping its own copy.
 */
private val LANGUAGE_BY_EXTENSION = mapOf(
    "ts" to "ts", "tsx" to "tsx", "mts" to "ts", "cts" to "ts",
    "js" to "js", "jsx" to "jsx", "mjs" to "js", "cjs" to "js",
    "json" to "json", "jsonc" to "json", "py" to "py", "rb" to "rb", "go" to "go", "rs" to "rs",
    "java" to "java", "c" to "c", "h" to "c", "cc" to "cpp", "cpp" to "cpp", "hpp" to "cpp",
    "cxx" to "cpp", "cs" to "cs", "kt" to "kotlin", "kts" to "kotlin", "swift" to "swift", "php" to "php",
    "sh" to "sh", "bash" to "sh", "zsh" to "sh", "yaml" to "yaml", "yml" to "yaml",
    "toml" to "toml", "ini" to "ini", "md" to "md", "markdown" to "md", "mdx" to "mdx",
    "html" to "html", "htm" to "html", "css" to "css", "scss" to "scss", "less" to "less",
    "sql" to "sql", "xml" to "xml", "lua" to "lua",
)

/**
 * Reads one workspace file on demand, for a panel that has no transcript preview.
 *
 * Injected so the panel stays host-free: the parent binds the session scope id
 * and the `workspaceFiles/read` call. A host refusal travels as
 * [FilePreview.Unavailable] rather than an exception, because the body renders
 * the host's own wording; an implementation may still throw, which the panel
 * renders the same way.
 */
fun interface WorkspaceFileLoader {
    suspend fun load(path: String): FilePreview
}
/**
 * Writes one workspace file back, returning the host's refusal or null on success.
 *
 * The counterpart of [WorkspaceFileLoader], and injected for the same reason: the
 * panel stays host-free and the parent binds the session. There is no
 * `workspaceFiles/write` on the host, so the production implementation goes through
 * the session's own shell — see `DshViewModel.workspaceFileSaver` for why, and for
 * the encoding that makes arbitrary text safe to send.
 *
 * A `String?` rather than an exception because a refusal is a *page-level fact* the
 * editor draws in place ("the file is gone", "the workspace refused the path"), not a
 * crash: the buffer the reader typed is still theirs and must survive it.
 */
fun interface WorkspaceFileSaver {
    suspend fun save(path: String, text: String): String?
}
/**
 * The shell line that writes [text] to [path], for hosts with no file-write RPC.
 *
 * ## The encoding problem
 *
 * The text is whatever the reader typed: it may contain single quotes, double quotes,
 * backticks, `$(...)`, a leading `-`, newlines, or a whole shell script. Every scheme
 * that quotes it into the command line is a bug waiting for the one input that breaks
 * out of the quoting — and the failure mode is not "the save failed", it is "the shell
 * ran something else". Base64 sidesteps the question entirely: the encoded form contains
 * only `A-Z a-z 0-9 + / =`, none of which mean anything to a shell inside single quotes.
 *
 * `printf %s` and `base64 -d` are both POSIX, so this works on the host's shell wherever
 * it runs. The output goes to a temporary file beside the target and is then renamed over
 * it, so an interrupted decode leaves the original intact instead of truncating it — the
 * difference between a refused save and a destroyed file.
 *
 * ## The path
 *
 * The path is single-quoted with the one escape single quotes require (`'\''`), which is
 * exact: a POSIX path cannot contain a NUL byte, and every other byte is representable
 * inside that quoting. Paths are workspace-relative as the host spells them, so they never
 * start with `-` in practice — but the quoting covers that too.
 */
fun fileWriteCommand(path: String, text: String): String {
    val encoded = java.util.Base64.getEncoder()
        .encodeToString(text.toByteArray(Charsets.UTF_8))
    // The temp name is derived from the *unquoted* path and then quoted once, so the
    // quoting cannot be applied twice — which would embed a literal quote pair in the
    // filename and write to a file nobody looks at.
    val quoted = shellSingleQuote(path)
    val quotedTemp = shellSingleQuote("$path.dsh-save")
    // `base64 -d` on GNU coreutils and on busybox both read from stdin; the `printf`
    // feeds it without a second temporary file. `&&` chains, so a decode failure never
    // reaches the rename.
    return "printf %s '$encoded' | base64 -d > $quotedTemp && mv -f $quotedTemp $quoted"
}

/**
 * [value] inside POSIX single quotes.
 *
 * The only character single quotes cannot hold is a single quote itself, and the way out
 * is to end the quoting, emit an escaped quote, and start again: `'` → `'\''`. Every
 * other byte — including newlines and every shell metacharacter — is literal inside the
 * quotes, which is the whole point of using them rather than double quotes.
 */
internal fun shellSingleQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"
/**
 * The language a Translate action asks the host for, from a system locale tag.
 *
 * The host's translator normalizes the common Chinese tags itself (`zh`, `zh-CN`,
 * `zh-SG`, `zh-Hans` to Simplified; `zh-TW`, `zh-HK`, `zh-MO`, `zh-Hant` to
 * Traditional) and passes anything else through, so this only has to answer the one
 * question it can answer better: what to send when the tag is *empty* or malformed.
 *
 * English is the fallback rather than a guess, because a translate button whose
 * target was silently invented is worse than one that says what it did.
 */
fun translationTargetOf(localeTag: String?): String {
    val tag = localeTag?.trim().orEmpty()
    if (tag.isEmpty()) return "en"
    // A locale tag is `language[-Script][-REGION]`; the host wants a language tag and
    // normalizes the variants itself, so the language subtag is what travels. Keeping
    // the region would also be valid — but only for the Chinese tags, and dropping it
    // for every other locale keeps this one rule instead of two.
    val language = tag.substringBefore('-').substringBefore('_').lowercase()
    return if (language.length in 2..3 && language.all { it.isLetter() }) language else "en"
}

/**
 * The string a `cotTranslation/translate` reply carries.
 *
 * The descriptor declares a plain string result, so that is the normal shape. A host
 * that answered `{"text": "..."}` instead is also accepted — not because the protocol
 * says so, but because a *missing* translation and an unreadable one look identical on
 * screen, and telling them apart is worth four lines.
 */
fun translationTextOf(value: Any?): String? = when (value) {
    is String -> value.takeIf { it.isNotBlank() }
    is org.json.JSONObject ->
        (value.opt("text") as? String)?.takeIf { it.isNotBlank() }
            ?: (value.opt("translation") as? String)?.takeIf { it.isNotBlank() }
    else -> null
}

/**
 * The three access modes this app draws distinctly, as a value rather than an icon.
 *
 * [uk.xa0.dsh.ui.components.ComposerAccessoryRail] turns these into `ImageVector`s; the
 * *classification* lives here because it is the part that can be wrong in a way a picture
 * cannot show — an unknown preset must fall back to a neutral glyph rather than to a
 * shield that implies a permission level the host never granted.
 *
 * The pill used to draw one shield for every mode, so a reader had to open the sheet to
 * see which of the three they were in. The mode is the most consequential state on that
 * row: it decides whether the agent may write to the workspace or run anything at all.
 */
enum class AccessModeGlyph {
    /** Reads without writing: the eye, not the lock. */
    READ_ONLY,

    /** Writes inside the workspace: a pen, which is what the permission is *for*. */
    WORKSPACE_WRITE,

    /** Unconfined: a filled shield, the strongest of the three. */
    FULL_ACCESS,

    /**
     * A preset this build does not know.
     *
     * Deliberately its own value rather than reusing one of the three: a host that adds a
     * fourth mode should be drawn as "something else" and not as the most permissive, nor
     * as the most restrictive.
     */
    OTHER,
}

/**
 * Which glyph [preset] wears.
 *
 * Matched case-insensitively and by substring, because the host has spelled the tree
 * preset as `workspace-write`, `workspaceWrite` and `workspace_write` across descriptor
 * versions, and a mode that only sometimes gets its own icon would be worse than one
 * that never does.
 */
fun accessModeGlyphOf(preset: String?): AccessModeGlyph {
    val key = preset?.trim()?.lowercase().orEmpty()
    if (key.isEmpty()) return AccessModeGlyph.OTHER
    return when {
        key.contains("read") && key.contains("only") -> AccessModeGlyph.READ_ONLY
        key.contains("readonly") -> AccessModeGlyph.READ_ONLY
        key.contains("workspace") -> AccessModeGlyph.WORKSPACE_WRITE
        key.contains("full") -> AccessModeGlyph.FULL_ACCESS
        key.contains("unrestricted") || key.contains("unconfined") -> AccessModeGlyph.FULL_ACCESS
        else -> AccessModeGlyph.OTHER
    }
}