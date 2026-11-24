package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import io.mockk.mockk
import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import jp.co.soramitsu.account.impl.presentation.RecyclerTestHost
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChainAccountsAdapterTest {
    private val clicked = mutableListOf<String>()
    private val options = mutableListOf<String>()
    private val adapter = ChainAccountsAdapter(object : ChainAccountsAdapter.Handler {
        override fun chainAccountClicked(item: AccountInChainUi) { clicked += item.chainId }
        override fun chainAccountOptionsClicked(item: AccountInChainUi) { options += item.chainId }
    }, mockk<ImageLoader>(relaxed = true))
    private lateinit var host: RecyclerTestHost

    private fun item(
        chainId: String,
        accountName: String? = null,
        from: AccountInChain.From? = AccountInChain.From.META_ACCOUNT,
        enabled: Boolean = true,
        isSupported: Boolean = true,
        hasAccount: Boolean = true,
        icon: Boolean = true
    ) = AccountInChainUi(
        chainId = chainId, chainName = "Name $chainId", chainIcon = "https://icons/$chainId.svg", address = "addr-$chainId",
        accountIcon = if (icon) ColorDrawable() else null, enabled = enabled, accountName = accountName, accountFrom = from,
        isSupported = isSupported, hasAccount = hasAccount, markedAsNotNeed = false
    )

    @Before
    fun setUp() {
        adapter.submitList(
            listOf(
                TextHeader("Shared secret"),
                item("polkadot", accountName = "Main"),
                item("acala", accountName = "Custom", from = AccountInChain.From.CHAIN_ACCOUNT),
                item("karura", enabled = false, from = null, hasAccount = false, icon = false),
                item("old", isSupported = false)
            )
        )
        host = RecyclerTestHost(adapter)
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    @Test
    fun `a supported account shows its chain, address, badge and icon and offers its actions`() {
        val row = host.itemView(1)
        assertEquals("Name polkadot", row.text(R.id.chainAccountChainName))
        assertEquals("addr-polkadot", row.text(R.id.chainAccountAccountAddress))
        assertEquals("Main", row.text(R.id.chainAccountNameBadge))
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.chainAccountNameBadge).visibility)
        assertNotNull(row.findViewById<View>(R.id.chainAccountNameBadge).background)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.chainAccountAccountIcon).visibility)

        row.findViewById<View>(R.id.labeledTextAction).performClick()
        row.performClick()
        assertEquals(listOf("polkadot"), options)
        assertEquals(listOf("polkadot"), clicked)
        assertNotNull(host.itemView(2).findViewById<View>(R.id.chainAccountNameBadge).background)
    }

    @Test
    fun `an account without an address hides its actions, badge and icon`() {
        val row = host.itemView(3)
        assertEquals(View.GONE, row.findViewById<View>(R.id.labeledTextAction).visibility)
        assertEquals(View.GONE, row.findViewById<View>(R.id.chainAccountNameBadge).visibility)
        assertEquals(View.GONE, row.findViewById<View>(R.id.chainAccountAccountIcon).visibility)
        row.performClick()
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `an unsupported chain is dimmed, says so and still reports taps for the update prompt`() {
        val row = host.itemView(4)
        assertEquals(host.context.getString(R.string.common_unsupported), row.text(R.id.chainAccountAccountAddress))
        assertEquals(View.GONE, row.findViewById<View>(R.id.labeledTextAction).visibility)
        assertTrue((row as ViewGroup).getChildAt(0).alpha < 1f)
        assertNotNull(row.findViewById<ImageView>(R.id.chainAccountAccountIcon).drawable)
        row.performClick()
        assertEquals(listOf("old"), clicked)
        assertNull(options.firstOrNull())
    }
}
