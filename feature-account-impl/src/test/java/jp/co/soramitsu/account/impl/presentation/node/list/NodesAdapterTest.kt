package jp.co.soramitsu.account.impl.presentation.node.list

import android.view.View
import android.widget.TextView
import jp.co.soramitsu.account.impl.presentation.RecyclerTestHost
import jp.co.soramitsu.account.impl.presentation.node.model.NodeHeaderModel
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NodesAdapterTest {
    private val info = mutableListOf<NodeModel>()
    private val checks = mutableListOf<NodeModel>()
    private val deletes = mutableListOf<NodeModel>()
    private val adapter = NodesAdapter(object : NodesAdapter.NodeItemHandler {
        override fun infoClicked(nodeModel: NodeModel) { info += nodeModel }
        override fun checkClicked(nodeModel: NodeModel) { checks += nodeModel }
        override fun deleteClicked(nodeModel: NodeModel) { deletes += nodeModel }
    })
    private lateinit var host: RecyclerTestHost

    private val active = NodeModel("Default", "wss://default", isDefault = true, isActive = true)
    private val custom = NodeModel("Mine", "wss://mine", isDefault = false, isActive = false)

    @Before
    fun setUp() {
        adapter.submitList(listOf(NodeHeaderModel("Default nodes"), active, NodeHeaderModel("Custom nodes"), custom))
        host = RecyclerTestHost(adapter)
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun `headers and nodes show their titles, hosts and the active check`() {
        assertEquals("Default nodes", host.itemView(0).text(R.id.nodeGroupTitle))
        assertFalse("auto selection disables the header", host.itemView(0).isEnabled)
        assertEquals("Default", host.itemView(1).text(R.id.nodeTitle))
        assertEquals("wss://default", host.itemView(1).text(R.id.nodeHost))
        assertEquals(View.VISIBLE, host.itemView(1).findViewById<View>(R.id.nodeCheck).visibility)
        assertEquals(View.INVISIBLE, host.itemView(3).findViewById<View>(R.id.nodeCheck).visibility)
    }

    @Test
    fun `with auto selection a tap selects nothing while info still opens`() {
        host.itemView(3).performClick()
        host.itemView(3).findViewById<View>(R.id.nodeInfo).performClick()

        assertTrue(checks.isEmpty())
        assertEquals(listOf(custom), info)
    }

    @Test
    fun `with manual selection a tap selects the node`() {
        adapter.handleAutoSelected(false)
        host.layout()

        assertTrue(host.itemView(0).isEnabled)
        assertTrue(host.itemView(1).findViewById<View>(R.id.nodeTitle).isEnabled)
        host.itemView(3).performClick()
        assertEquals(listOf(custom), checks)
    }

    @Test
    fun `edit mode offers deletion only for inactive custom nodes`() {
        adapter.handleAutoSelected(false)
        adapter.switchToEdit(true)
        host.layout()

        val customRow = host.itemView(3)
        assertEquals(View.VISIBLE, customRow.findViewById<View>(R.id.nodeDelete).visibility)
        assertEquals(View.INVISIBLE, customRow.findViewById<View>(R.id.nodeInfo).visibility)
        assertFalse(customRow.isEnabled)
        customRow.findViewById<View>(R.id.nodeDelete).performClick()
        assertEquals(listOf(custom), deletes)

        val defaultRow = host.itemView(1)
        assertEquals(View.GONE, defaultRow.findViewById<View>(R.id.nodeDelete).visibility)
        defaultRow.performClick()
        assertTrue("no selection while editing", checks.isEmpty())

        adapter.switchToEdit(false)
        host.layout()
        assertEquals(View.GONE, host.itemView(3).findViewById<View>(R.id.nodeDelete).visibility)
    }

    @Test
    fun `edit mode on a list without custom nodes changes nothing`() {
        adapter.submitList(listOf(NodeHeaderModel("Default nodes"), active))
        host.layout()
        adapter.switchToEdit(true)
        host.layout()
        assertEquals(View.GONE, host.itemView(1).findViewById<View>(R.id.nodeDelete).visibility)
    }
}
