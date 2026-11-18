package jp.co.soramitsu.wallet.impl.presentation.common

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import java.time.Duration
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/**
 * The actions behind a swipe are also the row's accessibility actions. They share the visible buttons'
 * double-tap guard, so a repeated screen-reader or Switch Access action opens one Send screen, not two.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
class SwipeableAssetListItemAccessibilityTest {

    @get:Rule
    val compose = createWalletComposeRule()

    private val clicked = mutableListOf<ActionItemType>()

    @Before
    fun setUp() {
        ComposeFixtures.prepareUiTest()
        compose.setContent {
            FearlessAppTheme {
                SwipeableAssetListItem(
                    assetState = ComposeFixtures.assetRow(),
                    isHideVisible = true,
                    assetClicked = {},
                    actionItemClicked = { type, _, _, _ -> clicked += type }
                )
            }
        }
    }

    @Test
    fun aRepeatedAccessibilityActionWithinTheGuardRunsOnce() {
        val send = action(0)

        compose.runOnUiThread {
            send()
            send()
        }

        assertEquals(listOf(ActionItemType.SEND), clicked)
    }

    @Test
    fun anAccessibilityActionAfterTheGuardRunsAgain() {
        val send = action(0)

        compose.runOnUiThread {
            send()
            ShadowSystemClock.advanceBy(Duration.ofMillis(1_600))
            send()
        }

        assertEquals(listOf(ActionItemType.SEND, ActionItemType.SEND), clicked)
    }

    private fun action(index: Int): () -> Unit {
        val actions = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals("Send, Receive and the visibility action", 3, actions.size)
        return { actions[index].action() }
    }
}
