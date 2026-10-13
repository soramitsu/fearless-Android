package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import jp.co.soramitsu.account.impl.domain.account.details.AccountInChain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAccountActionsTest {
    @Test
    fun `offline address payload copies the address without node or export targets`() {
        val item = account(isOffline = true)
        val payload = item.offlineActionsPayload()!!
        assertEquals(item.address, payload.value)
        assertEquals(item.chainName, payload.chainName)
        assertNull(payload.chainId)
        assertFalse(payload.canSwitchNode)
        assertTrue(payload.explorers.isEmpty())
    }

    @Test
    fun `live accounts retain their normal actions`() {
        assertNull(account(isOffline = false).offlineActionsPayload())
    }

    private fun account(isOffline: Boolean) = AccountInChainUi(
        chainId = "sora-nexus-mainnet", chainName = "SORA Nexus", chainIcon = "", address = "offline-address",
        accountIcon = null, accountName = null, accountFrom = AccountInChain.From.META_ACCOUNT,
        hasAccount = true, markedAsNotNeed = false, isOffline = isOffline
    )
}
