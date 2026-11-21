package jp.co.soramitsu.wallet.impl.presentation

import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import org.junit.Test

/** The arguments a caller gets when it leaves the optional navigation parameters out. */
class WalletRouterDefaultsCoverageTest {

    private val router = mockk<WalletRouter>(relaxed = true)
    private val payload = AssetPayload("polkadot", "dot")

    @Test
    fun sendScreensOpenWithoutPrefilledValues() {
        val draft = TransferDraft(BigDecimal.ONE, BigDecimal.ZERO, payload, RECIPIENT, null, null)

        router.openSend(payload)
        router.openLockedAmountSend(payload, amount = BigDecimal.TEN)
        router.openSendConfirm(draft, phishingType = null)

        verify { router.openSend(payload, null, null, null) }
        verify { router.openLockedAmountSend(payload, null, null, BigDecimal.TEN) }
        verify { router.openSendConfirm(draft, null, emptyMap(), null, false) }
    }

    @Test
    fun networkPickersOpenInTheirDefaultModes() {
        router.openSelectChain(assetId = "dot")
        router.openSelectChain()
        router.openSelectChainForXcm(selectedChainId = "polkadot", xcmChainType = XcmChainType.Origin)

        verify { router.openSelectChain("dot", null, true, true, false) }
        verify { router.openSelectChain(null, null, true, null, true, true, false) }
        verify { router.openSelectChainForXcm("polkadot", XcmChainType.Origin, null, null, null) }
    }

    @Test
    fun filterOptionsAndManageAssetsOpenUnrestricted() {
        router.openFilter()
        router.openOptionsWallet(7L)
        router.openManageAssets()

        verify { router.openFilter(TransactionFilter.entries.toSet()) }
        verify { router.openOptionsWallet(7L, true) }
        verify { router.openManageAssets("All") }
    }

    private companion object {
        const val RECIPIENT = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    }
}
