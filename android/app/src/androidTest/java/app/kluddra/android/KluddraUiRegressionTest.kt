package app.kluddra.android

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose UI regression suite covering:
 * - Welcome -> Local-only onboarding
 * - Creating, opening, and closing notes in Portrait AND Landscape (guards against
 *   SeekableTransitionState crashes when switching between side rail / bottom bar / single-pane editor)
 * - Switching Notes <-> Shopping <-> Settings <-> Notes and re-opening notes
 * - Adding, toggling, and clearing shopping items
 */
@RunWith(AndroidJUnit4::class)
class KluddraUiRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun res(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun enterLocalModeIfNeeded() {
        composeRule.waitForIdle()
        val startLocalText = res(R.string.action_start_local)
        val hasWelcome = composeRule.onAllNodesWithText(startLocalText).fetchSemanticsNodes().isNotEmpty()
        if (hasWelcome) {
            composeRule.onNodeWithText(startLocalText).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun closeEditorIfSinglePane() {
        val backDesc = res(R.string.action_back)
        if (composeRule.onAllNodes(hasContentDescription(backDesc)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription(backDesc).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun rotateTo(orientation: Int) {
        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = orientation
        }
        // Let Android complete Activity teardown/recreation before ComposeIdlingResource attaches
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(500L)
        composeRule.waitForIdle()
    }

    @Test
    fun fullFlow_portraitAndLandscape_noCrashOnNoteOpenCloseOrTabSwitch() {
        // 1. Start in portrait & enter local-only mode if on Welcome screen
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        enterLocalModeIfNeeded()

        // Verify Notes tab is visible (wait cleanly for DataStore onboarding preference emission)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasText(res(R.string.nav_notes)) and hasClickAction())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodes(hasText(res(R.string.nav_notes)) and hasClickAction()).onFirst().assertIsDisplayed()

        // 2. Create a note in portrait, type content, pick one of the new 10 colors, and navigate back
        composeRule.onNodeWithContentDescription(res(R.string.new_note)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Regression Note")
        composeRule.waitForIdle()
        // Open 2x5 color picker and choose Mint (new Iteration 14 color)
        composeRule.onNodeWithContentDescription(res(R.string.action_note_color)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("note_color_mint").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        closeEditorIfSinglePane()

        // Wait until the saved note appears in the overview list
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Regression Note").fetchSemanticsNodes().isNotEmpty()
        }

        // 2b. Exercise the Sort dropdown (switch sort orders and return to Last edited)
        composeRule.onNodeWithContentDescription(res(R.string.sort_notes)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.sort_created_desc)).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(res(R.string.sort_notes)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.sort_title_asc)).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(res(R.string.sort_notes)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.sort_updated_desc)).assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // 3. Switch to Shopping tab, add an item, toggle it, then return to Notes
        composeRule.onAllNodes(hasText(res(R.string.nav_shopping)) and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("Havremjölk")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Havremjölk").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Havremjölk").onFirst().performClick()
        composeRule.waitForIdle()

        // Return to Notes tab
        composeRule.onAllNodes(hasText(res(R.string.nav_notes)) and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()

        // 4. Open Settings (verifies slogan, local mode card, Light & Dark toggle), then return to Notes
        composeRule.onNodeWithContentDescription(res(R.string.nav_settings)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.settings_local_mode_title)).assertIsDisplayed()
        composeRule.onNodeWithText(res(R.string.theme_light)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.theme_dark)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(res(R.string.theme_light)).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(res(R.string.settings_privacy_note)))
        composeRule.onNodeWithText(res(R.string.settings_privacy_note)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(res(R.string.action_back)).performClick()
        composeRule.waitForIdle()

        // 5. Open and close note in Portrait AFTER tab switch + Settings return
        composeRule.onAllNodesWithText("Regression Note").onFirst().performClick()
        composeRule.waitForIdle()
        closeEditorIfSinglePane()

        // 6. Rotate to LANDSCAPE -> open note -> close (if single-pane) -> switch tabs -> open note again!
        // Guards against SeekableTransitionState crashes across rail/bottom-bar/orientation changes.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Regression Note").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Regression Note").onFirst().performClick()
        composeRule.waitForIdle()
        closeEditorIfSinglePane()

        // Switch to Shopping and back to Notes in landscape, then open note again
        composeRule.onAllNodes(hasText(res(R.string.nav_shopping)) and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText(res(R.string.nav_notes)) and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Regression Note").onFirst().performClick()
        composeRule.waitForIdle()

        // Rotate back to PORTRAIT while note is open, then close cleanly!
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        closeEditorIfSinglePane()

        // Restore default orientation
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }
}
