package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DappSigningPauseTest {

    @Test
    fun `every method follows the switch of its family`() {
        val polkadotPaused = featureToggleStore().apply {
            rememberRemoteVetoes(FeatureToggleConfig(polkadotDappSigningEnabled = false))
        }
        val evmPaused = featureToggleStore().apply {
            rememberRemoteVetoes(FeatureToggleConfig(evmDappSigningEnabled = false))
        }

        WalletConnectMethod.values().forEach { method ->
            val isPolkadot = method.method.startsWith("polkadot_")
            assertEquals(method.method, isPolkadot, polkadotPaused.pausesDappSigning(method.method))
            assertEquals(method.method, !isPolkadot, evmPaused.pausesDappSigning(method.method))
        }
    }

    @Test
    fun `nothing is paused without a veto, and the TON switch does not pause WalletConnect`() {
        val tonPaused = featureToggleStore().apply {
            rememberRemoteVetoes(FeatureToggleConfig(tonDappSigningEnabled = false))
        }

        WalletConnectMethod.values().forEach { method ->
            assertFalse(method.method, featureToggleStore().pausesDappSigning(method.method))
            assertFalse(method.method, tonPaused.pausesDappSigning(method.method))
        }
    }
}
