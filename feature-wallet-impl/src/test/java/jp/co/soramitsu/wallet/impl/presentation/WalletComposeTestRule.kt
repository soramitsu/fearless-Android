package jp.co.soramitsu.wallet.impl.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule

/** Advance bounded frames for UI assertions even when a loading indicator never stops animating. */
internal fun createWalletComposeRule(): ComposeContentTestRule = WalletComposeTestRule(createComposeRule())

private class WalletComposeTestRule(
    private val delegate: ComposeContentTestRule
) : ComposeContentTestRule by delegate {

    override fun setContent(content: @Composable () -> Unit) {
        delegate.mainClock.autoAdvance = false
        delegate.setContent(content)
        waitForIdle()
    }

    override fun waitForIdle() {
        repeat(2) {
            delegate.mainClock.advanceTimeBy(250)
            delegate.waitForIdle()
        }
    }

    override fun onNode(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteraction {
        waitForIdle()
        return delegate.onNode(matcher, useUnmergedTree)
    }

    override fun onAllNodes(matcher: SemanticsMatcher, useUnmergedTree: Boolean): SemanticsNodeInteractionCollection {
        waitForIdle()
        return delegate.onAllNodes(matcher, useUnmergedTree)
    }
}
