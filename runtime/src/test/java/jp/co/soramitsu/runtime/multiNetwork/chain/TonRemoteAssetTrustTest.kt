package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import jp.co.soramitsu.common.model.AssetMetadataTrust

class TonRemoteAssetTrustTest {

    @Test
    fun `verified jetton price identity is its master address`() {
        val mapped = jetton(master = "master-a", symbol = "DUP", verification = "whitelist")
            .toChainAssetLocal("ton")

        assertEquals("master-a", mapped.id)
        assertEquals("master-a", mapped.currencyId)
        assertEquals("master-a", mapped.priceId)
        assertEquals(AssetMetadataTrust.Verified, jetton("master-a", "DUP", "whitelist").metadataTrust())
    }

    @Test
    fun `unverified same symbol jetton cannot inherit verified price identity`() {
        val verified = jetton(master = "master-a", symbol = "DUP", verification = "verified")
            .toChainAssetLocal("ton")
        val unverified = jetton(master = "master-b", symbol = "DUP", verification = "none")
            .toChainAssetLocal("ton")

        assertEquals("master-a", verified.priceId)
        assertNull(unverified.priceId)
        assertEquals("master-b", unverified.id)
        assertEquals(AssetMetadataTrust.Unverified, jetton("master-b", "DUP", "none").metadataTrust())
    }

    private fun jetton(master: String, symbol: String, verification: String): JettonBalance {
        return JettonBalance(
            balance = "1",
            walletAddress = AccountAddress(master, false, true),
            jetton = JettonPreview(master, symbol, symbol, 9, "", verification)
        )
    }
}
