package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.compose.theme.fw.FwSyncPhase
import jp.co.soramitsu.common.domain.model.NetworkIssueType
import org.junit.Assert.assertEquals
import org.junit.Test

class SunlitWalletHeroTest {
    private val loaded = WalletState.default.copy(
        assetsState = WalletAssetsState.Assets(AssetsLoadingState.Loaded(emptyList()), isHideVisible = true)
    )

    @Test fun heroSunReportsTheWalletsRealLoadingState() {
        assertEquals(FwSyncPhase.Running, walletSyncState(WalletState.default).phase)
        assertEquals(FwSyncPhase.Done, walletSyncState(loaded).phase)
        assertEquals(FwSyncPhase.Failed, walletSyncState(loaded.copy(hasNetworkIssues = true)).phase)
        val issue = loaded.copy(assetsState = WalletAssetsState.NetworkIssue("chain", NetworkIssueType.Node, retryButtonLoading = false))
        assertEquals(FwSyncPhase.Failed, walletSyncState(issue).phase)
        // Progress is unknown while assets load: the arc shows a quarter turn, never an invented value.
        assertEquals(null, walletSyncState(WalletState.default).progress)
    }

    @Test fun addressChipShowsBothEndsAndShortAddressesWhole() {
        assertEquals("5GrwvaEF", heroAddressLabel("5GrwvaEF"))
        assertEquals("5Grwva…GKutQY", heroAddressLabel("5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"))
        assertEquals("0x7a25…f8c4e1", heroAddressLabel("0x7a2512b1b6ab6c80c8e6c2ef1f8c4e1"))
    }
}
