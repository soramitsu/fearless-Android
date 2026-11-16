package jp.co.soramitsu.account.impl.presentation.node.list

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.account.impl.testutil.closeFragmentHosts
import jp.co.soramitsu.account.impl.testutil.hostFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodesBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NodesFragmentTest {

    private val vm = mockk<NodesViewModel>(relaxed = true)
    private val parity = NodeModel("Parity", "wss://rpc.polkadot.io", isDefault = true, isActive = true)
    private val custom = NodeModel("Home node", "wss://node.example.org", isDefault = false, isActive = false)
    private val nodes = MutableLiveData<List<Any>>(listOf(NodeHeaderModel("Default nodes"), parity, custom))
    private val editMode = MutableLiveData(false)
    private val deleteEvent = MutableLiveData<Event<NodeModel>>()
    private val autoSelected = MutableStateFlow(false)
    private val supportsAutoSelection = MutableLiveData(true)
    private val selectionPending = MutableLiveData(false)
    private val hasCustomNodes = MutableLiveData(true)
    private lateinit var fragment: NodesFragment
    private lateinit var binding: FragmentNodesBinding

    @After
    fun cleanUpHosts() {
        closeFragmentHosts()
    }

    private fun host() {
        every { vm.groupedNodeModelsLiveData("polkadot") } returns nodes
        every { vm.editMode } returns editMode
        every { vm.toolbarAction } returns MutableLiveData("Edit")
        every { vm.deleteNodeEvent } returns deleteEvent
        every { vm.chainName } returns MutableLiveData("Polkadot")
        every { vm.autoSelectedNodeFlow } returns autoSelected
        every { vm.supportsAutoSelection } returns supportsAutoSelection
        every { vm.selectionPending } returns selectionPending
        every { vm.hasCustomNodeModelsLiveData } returns hasCustomNodes
        fragment = NodesFragment().apply {
            arguments = NodesFragment.getBundle("polkadot")
            imageLoader = mockk(relaxed = true)
        }
        hostFragment(fragment, vm)
        binding = FragmentNodesBinding.bind(fragment.requireView())
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun toolbarAndButtonsShowTheChainAndForwardNavigation() {
        host()

        assertEquals("Polkadot", binding.nodesToolbar.findViewById<TextView>(jp.co.soramitsu.common.R.id.titleTv).text.toString())
        assertEquals("Edit", binding.nodesToolbar.rightActionText.text.toString())
        assertTrue(binding.nodesToolbar.rightActionText.isEnabled)

        binding.nodesToolbar.rightActionText.performClick()
        binding.addNodeButton.performClick()
        binding.nodesToolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        verify { vm.editClicked() }
        verify { vm.addNodeClicked() }
        verify { vm.backClicked() }
        assertEquals(3, binding.connectionsList.adapter?.itemCount)
    }

    @Test
    fun pendingSelectionUnsupportedAutoSelectionAndNoCustomNodesDisableControls() {
        host()

        selectionPending.value = true
        assertFalse(binding.addNodeButton.isEnabled)
        assertFalse(binding.autoSelectNodesSwitch.isEnabled)
        selectionPending.value = false
        assertTrue(binding.addNodeButton.isEnabled)

        supportsAutoSelection.value = false
        assertEquals(View.GONE, binding.autoSelectNodesContainer.visibility)
        supportsAutoSelection.value = true
        assertEquals(View.VISIBLE, binding.autoSelectNodesContainer.visibility)

        hasCustomNodes.value = false
        assertFalse(binding.nodesToolbar.rightActionText.isEnabled)
    }

    @Test
    fun autoSelectionSwitchFollowsAndWritesTheSetting() {
        host()

        autoSelected.value = true
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(binding.autoSelectNodesSwitch.isChecked)
        assertTrue(binding.autoSelectNodesLabel.isEnabled)

        binding.autoSelectNodesSwitch.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(autoSelected.value)
        assertFalse(binding.autoSelectNodesLabel.isEnabled)
    }

    @Test
    fun nodeRowActionsReachTheViewModel() {
        host()

        fragment.checkClicked(custom)
        fragment.infoClicked(parity)
        fragment.deleteClicked(custom)

        verify { vm.selectNodeClicked(custom) }
        verify { vm.infoClicked(parity) }
        verify { vm.deleteNodeClicked(custom) }
    }

    @Test
    fun deletingACustomNodeAsksForConfirmationNamingTheNode() {
        host()

        deleteEvent.value = Event(custom)
        fragment.childFragmentManager.executePendingTransactions()

        val dialog = fragment.childFragmentManager.fragments.filterIsInstance<ErrorDialog>().single()
        val arguments = dialog.requireArguments()
        assertEquals("Home node", arguments.getString("error_dialog_message"))
        assertEquals(fragment.getString(R.string.delete_custom_node_title), arguments.getString("error_dialog_title"))
        assertEquals(fragment.getString(R.string.common_delete), arguments.getString("error_dialog_positive_button_text"))
    }
}
