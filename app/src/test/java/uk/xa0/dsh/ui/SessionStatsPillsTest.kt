package uk.xa0.dsh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.xa0.dsh.model.SessionStats
import uk.xa0.dsh.scroll.HarnessApplication
import uk.xa0.dsh.ui.components.STATS_CACHE_PILL_TAG
import uk.xa0.dsh.ui.components.STATS_SPEED_PILL_TAG
import uk.xa0.dsh.ui.components.SessionCachePill
import uk.xa0.dsh.ui.components.SessionSpeedPill
import uk.xa0.dsh.ui.theme.DshTheme

/**
 * The two session readings, as rail pills: output speed and cache share.
 *
 * The old row drew whole sentences (`25 turns 416 steps · 123.4 tok/s`) and pinned one
 * chip to each edge of the composer card. Round 2 replaced that layout: the readings are
 * one figure each in the control rail, and each gates on its own reading. What is pinned
 * here is exactly the part the redesign could get wrong:
 *
 *  * a session with token activity and no closed step draws the cache pill alone (a real
 *    host shape — the connected host serves `tokenUsage` without `sessionStats`);
 *  * a session with closed steps and no billing draws the speed pill alone;
 *  * neither pill invents a figure the host did not report (no `0 tok/s`, no `Cache 0%`
 *    when there was no prompt-side input at all);
 *  * each pill opens its own panel, and a pill with no timing is a plain reading rather
 *    than a button onto an empty panel.
 *
 * No pixel assertions: this harness cannot measure text, and the rail is a scroll whose
 * order the screenshots pin. The tags address the pills as placed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HarnessApplication::class, qualifiers = "w411dp-h891dp")
class SessionStatsPillsTest {

    @get:Rule
    val rule = createComposeRule()

    private companion object {
        /**
         * The reported screen: 16.3K tokens billed, 48% of the prompt served from
         * cache, and no closed step yet — so the speed pill has no reading to draw.
         */
        val TOKEN_STRIP_ONLY = SessionStats(
            hasStats = false,
            turns = 0,
            steps = 0,
            hasUsage = true,
            uncachedInputTokens = 8_200,
            cacheReadTokens = 7_600,
            cacheWriteTokens = 0,
            outputTokens = 500,
        )

        /**
         * A session that has closed steps *and* been billed: 209 tok/s from the
         * host's own decode counters, 99% of the prompt served from cache.
         */
        val BOTH_READINGS = TOKEN_STRIP_ONLY.copy(
            hasStats = true,
            turns = 25,
            steps = 416,
            llmMs = 3_042_057,
            toolMs = 8_306_312,
            ttftMs = 1_133_161,
            ttftSteps = 416,
            decodeMs = 1_908_896,
            decodeTokens = 399_913,
            uncachedInputTokens = 361_258,
            cacheReadTokens = 81_880_960,
            outputTokens = 399_913,
        )

        /** Closed steps with timing settled, and a billing projection of nothing. */
        val COUNTS_ONLY = SessionStats(
            hasStats = true,
            turns = 1,
            steps = 13,
            llmMs = 48_512,
            toolMs = 235_889,
            ttftMs = 28_709,
            ttftSteps = 13,
            decodeMs = 19_803,
            decodeTokens = 4_168,
            hasUsage = true,
        )

        /** Steps exist but nothing was timed: `hasTiming` is false. */
        val UNTIMED = COUNTS_ONLY.copy(
            llmMs = 0,
            toolMs = 0,
            ttftSteps = 0,
            decodeMs = 0,
            decodeTokens = 0,
            hasUsage = false,
        )
    }

    private fun compose(content: @Composable () -> Unit = {}) {
        rule.setContent {
            DshTheme {
                Box(Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
        rule.waitForIdle()
    }

    private fun composeBoth(stats: SessionStats?) {
        compose {
            SessionSpeedPill(stats = stats, backdrop = null)
            SessionCachePill(stats = stats, backdrop = null)
        }
    }

    @Test
    fun `both readings are drawn with one figure each`() {
        composeBoth(BOTH_READINGS)

        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).assertExists()
        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).assertExists()
        rule.onNodeWithText("209 tok/s", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Cache 99.5%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a token strip with no closed step draws only the cache pill`() {
        composeBoth(TOKEN_STRIP_ONLY)

        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).assertExists()
        rule.onNodeWithText("Cache 48%", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `closed steps without billing draw only the speed pill`() {
        composeBoth(COUNTS_ONLY.copy(hasUsage = false))

        // COUNTS_ONLY has timing (decodeMs > 0) but no billed input or output, so the
        // cache pill has nothing to report; the speed pill does.
        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).assertExists()
        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).assertDoesNotExist()
        rule.onNodeWithText("210 tok/s", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `no projection draws nothing at all`() {
        composeBoth(null)

        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the speed pill opens the session statistics panel`() {
        compose {
            SessionSpeedPill(stats = BOTH_READINGS, backdrop = null)
        }

        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Session statistics").assertIsDisplayed()
        rule.onNodeWithText("Tokens per second (TPS)").assertIsDisplayed()
    }

    @Test
    fun `the cache pill opens the token usage panel and its balance door`() {
        compose {
            SessionCachePill(stats = BOTH_READINGS, backdrop = null)
        }

        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Token usage").assertIsDisplayed()
        rule.onNodeWithText("Cache hit").assertIsDisplayed()
        rule.onNodeWithText("Balance & top up").assertIsDisplayed()
    }

    @Test
    fun `an untimed reading is not a button onto an empty panel`() {
        compose {
            SessionSpeedPill(stats = UNTIMED, backdrop = null)
            SessionCachePill(stats = UNTIMED, backdrop = null)
        }

        // No speed (nothing was timed) and no cache share (nothing was billed): both
        // pills are absent rather than empty shells.
        rule.onNodeWithTag(STATS_SPEED_PILL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(STATS_CACHE_PILL_TAG).assertDoesNotExist()
        rule.onNodeWithText("Session statistics").assertDoesNotExist()
    }
}
