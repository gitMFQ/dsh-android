package uk.xa0.dsh.model

// These rows used to live in the root package (DshViewModel.kt) while the rest of
// `model/` imported them from there, which is what kept `model/` from being
// self-contained. They are host rows, not view-model state: only the package and
// the file changed, no declaration did.

/**
 * One background job the host is running (or ran) for a session — a `JobView` off
 * the `job/list` stream.
 *
 * The roster used to arrive on the same control stream as the queue, as a `jobs`
 * block per session. The running 0.2.0-rc.2 host publishes no such block (probed:
 * its control baseline carries `projections` and nothing else), which is why jobs
 * had vanished from every session. They now come from `job/list` — one stream per
 * session — and a job that starts, stops or fails is still a push, not a poll.
 * See `model/Jobs.kt` for the wire rules and `docs/JOBS.md` for the probes.
 */
data class JobItem(
    val id: String,
    val kind: String,
    val label: String,
    /** Owning session; absent for an unowned job, which every caller can see. */
    val owner: String? = null,
    val status: String,
    /** The producer's live progress line, cleared at settlement. */
    val progress: String? = null,
    val detail: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    /** The output ring's coordinates, which decide whether a row can be expanded. */
    val output: JobOutput = JobOutput(),
) {
    val running: Boolean get() = status == "running" || status == "stopping"
}
