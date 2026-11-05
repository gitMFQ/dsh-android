package uk.xa0.dsh.diag

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import uk.xa0.dsh.data.LiquidGlassLevel
import uk.xa0.dsh.scroll.HarnessApplication

/**
 * The boot log that came with the glass crash showed seven launches in a row,
 * every one of them reporting "launch guard: no change", while the process died a
 * second or two after the first frame. The cause was not the counting: it was that
 * the start marker was deleted at that first frame, long before the chat screen
 * (and the RenderThread crash) existed.
 *
 * These tests pin the shape of the fix. [LaunchGuard.markStarted] now only
 * *schedules* the confirmation, so a process killed in the chat-screen window never
 * runs it and the marker survives to be counted by the next launch. The synchronous
 * write itself lives in [LaunchGuard.confirmStarted], which is what a live, healthy
 * start ends up calling once the delay elapses. Testing that write directly keeps
 * most of these tests independent of Robolectric looper scheduling; the one test
 * that calls [LaunchGuard.markStarted] pins the looper to PAUSED so the delayed
 * post can never fire mid-assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LaunchGuardTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `a start writes the marker and a confirmed start removes it`() {
        assertTrue("a fresh store has no start marker", prefs.getLong(KEY_STARTED_AT, 0L) == 0L)

        LaunchGuard.markLaunching(context)
        assertTrue("markLaunching leaves a non-zero marker", prefs.getLong(KEY_STARTED_AT, 0L) > 0L)

        LaunchGuard.confirmStarted(context)
        assertTrue("confirmStarted deletes the marker", prefs.getLong(KEY_STARTED_AT, 0L) == 0L)
        assertEquals("confirmStarted zeroes the streak", 0, prefs.getInt(KEY_CRASH_STREAK, -1))
    }

    @Test
    fun `markStarted schedules the confirmation instead of clearing the marker at once`() {
        LaunchGuard.markLaunching(context)
        assertTrue(prefs.getLong(KEY_STARTED_AT, 0L) > 0L)

        LaunchGuard.markStarted(context)

        // The whole bug: the old code deleted the marker here, at the first frame,
        // ~1.5 s before the chat screen crashed. A delayed post leaves it in place,
        // so the next launch can count this one as unfinished.
        assertTrue(
            "the marker must survive until the delayed confirmation runs",
            prefs.getLong(KEY_STARTED_AT, 0L) > 0L,
        )
    }

    @Test
    fun `the first unfinished start is counted but does not degrade yet`() {
        LaunchGuard.markLaunching(context)

        assertFalse(LaunchGuard.check(context))
        assertEquals(1, prefs.getInt(KEY_CRASH_STREAK, -1))
        assertTrue(LaunchGuard.forcedLevel(context) == null)
    }

    @Test
    fun `a second unfinished start steps the level down one rung`() {
        LaunchGuard.markLaunching(context)
        assertFalse(LaunchGuard.check(context))

        // The process died before the delayed confirmation could run: the marker is
        // still here, and onCreate of the next launch writes a fresh one over it.
        LaunchGuard.markLaunching(context)
        assertTrue(LaunchGuard.check(context))

        assertEquals(2, prefs.getInt(KEY_CRASH_STREAK, -1))
        assertEquals(LiquidGlassLevel.BLUR, LaunchGuard.forcedLevel(context))
        assertEquals(LiquidGlassLevel.BLUR, LaunchGuard.degradedLevel(context))
        assertEquals(
            LiquidGlassLevel.BLUR,
            prefs.getString(KEY_LIQUID_GLASS_LEVEL, null),
        )
    }

    @Test
    fun `a confirmed clean start breaks the streak`() {
        // One unfinished start: streak is 1.
        LaunchGuard.markLaunching(context)
        assertFalse(LaunchGuard.check(context))
        LaunchGuard.markLaunching(context)

        // The process stayed up: the delayed confirmation ran before it died.
        LaunchGuard.confirmStarted(context)
        assertTrue(prefs.getLong(KEY_STARTED_AT, 0L) == 0L)

        // The next launch sees no marker and starts counting from zero again.
        assertFalse(LaunchGuard.check(context))
        assertEquals(0, prefs.getInt(KEY_CRASH_STREAK, -1))
        assertTrue(LaunchGuard.forcedLevel(context) == null)
    }

    @Test
    fun `each further dead launch steps down another rung until the floor`() {
        assertEquals(LiquidGlassLevel.LIQUID, currentLevel())

        assertFalse(crashOnce()) // streak 1: counted, not enough to act
        assertTrue(crashOnce()) // streak 2 -> BLUR
        assertEquals(LiquidGlassLevel.BLUR, currentLevel())

        assertTrue(crashOnce()) // streak 3 -> BAR
        assertEquals(LiquidGlassLevel.BAR, currentLevel())

        assertTrue(crashOnce()) // streak 4 -> OFF
        assertEquals(LiquidGlassLevel.OFF, currentLevel())

        // OFF is the floor: the guard stays silent rather than pretending the
        // material is still the cause.
        assertFalse(crashOnce())
        assertFalse(crashOnce())
        assertEquals(LiquidGlassLevel.OFF, currentLevel())
    }

    @Test
    fun `reset restores the full material and clears the guard record`() {
        LaunchGuard.markLaunching(context)
        assertFalse(LaunchGuard.check(context))
        LaunchGuard.markLaunching(context)
        assertTrue(LaunchGuard.check(context))
        assertEquals(LiquidGlassLevel.BLUR, LaunchGuard.forcedLevel(context))

        LaunchGuard.reset(context)

        assertEquals(LiquidGlassLevel.LIQUID, currentLevel())
        assertTrue(LaunchGuard.forcedLevel(context) == null)
        assertTrue(LaunchGuard.degradedLevel(context).isEmpty())
    }

    /** One launch that wrote its marker and then died before confirmation. */
    private fun crashOnce(): Boolean {
        LaunchGuard.markLaunching(context)
        return LaunchGuard.check(context)
    }

    private fun currentLevel(): String =
        prefs.getString(KEY_LIQUID_GLASS_LEVEL, null)
            ?.let { LiquidGlassLevel.parse(it) }
            ?: LiquidGlassLevel.LIQUID

    private companion object {
        const val PREFS = "dsh_launch_guard"
        const val KEY_STARTED_AT = "startedAt"
        const val KEY_CRASH_STREAK = "crashStreak"
        const val KEY_LIQUID_GLASS_LEVEL = "liquidGlassLevel"
    }
}
