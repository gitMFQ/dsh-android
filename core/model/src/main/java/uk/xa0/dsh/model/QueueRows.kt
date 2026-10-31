package uk.xa0.dsh.model

// These rows used to live in the root package (DshViewModel.kt) while the rest of
// `model/` imported them from there, which is what kept `model/` from being
// self-contained. They are host rows, not view-model state: only the package and
// the file changed, no declaration did.

/** One attachment block carried by a queued message, for its chip. */
data class QueueAttachment(
    val kind: String,
    val name: String,
    val bytes: Int,
    /**
     * Base64 bytes of a locally staged picture. A host row never has them (it
     * carries a durable `attachmentId` instead); a local submission echo does,
     * because there is nothing to read back yet.
     */
    val data: String? = null,
)

/**
 * One still-pending inbox occurrence, as the host projects it (`inbox` /
 * `session/control` queue frames).
 *
 * [preview] is the host client's own flattening: every non-attachment block
 * becomes its text (or `[type]`), whitespace collapsed, capped at 200 chars —
 * that is what the web row shows. [text] is non-null only when *every* block is
 * text, which is precisely when the web allows editing it inline.
 */
data class QueuedMessage(
    val id: String,
    val placement: String,
    val preview: String,
    val text: String?,
    val attachments: List<QueueAttachment>,
    /**
     * The `session/prompt` request id the host echoed onto this row. It is the
     * only identity shared with a local submission echo, so it is what retires
     * one: the web's `admitted` set is built exactly this way.
     */
    val rpcId: String? = null,
    /**
     * A client-side submission echo, not yet a host row. It has no host item id
     * ([id] is the prompt request id), so its actions are disabled.
     */
    val pending: Boolean = false,
)
