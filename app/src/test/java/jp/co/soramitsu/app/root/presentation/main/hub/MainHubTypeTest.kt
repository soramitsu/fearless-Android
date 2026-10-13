package jp.co.soramitsu.app.root.presentation.main.hub

import jp.co.soramitsu.common.model.DeFiAccountRequirement
import jp.co.soramitsu.common.model.DeFiAvailability
import jp.co.soramitsu.common.model.DeFiCapability
import jp.co.soramitsu.common.model.DeFiSigningRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainHubTypeTest {

    @Test
    fun shouldResolveKnownHubTypesFromNavigationArguments() {
        assertEquals(MainHubType.Defi, MainHubType.fromWireValue("defi"))
        assertEquals(MainHubType.Polkaswap, MainHubType.fromWireValue("polkaswap"))
        assertEquals(MainHubType.CrossChain, MainHubType.fromWireValue("cross_chain"))
    }

    @Test
    fun shouldFallBackToPolkaswapForMissingArgument() {
        assertEquals(MainHubType.Polkaswap, MainHubType.fromWireValue(null))
    }

    @Test
    fun disclaimerReviewRemainsReachableWhileSwapMutationsArePaused() {
        val paused = DeFiCapability(
            featureId = "polkaswap",
            availability = DeFiAvailability.Available,
            accountRequirement = DeFiAccountRequirement.Sora,
            signingRequirement = DeFiSigningRequirement.SignableAccount,
            supportedNetworkIds = setOf("sora"),
            destinationEnabled = true,
            actionsEnabled = false,
            userFacingReason = "Swap actions are temporarily disabled."
        )

        assertTrue(PolkaswapCapability(paused, disclaimerAccepted = false).canOpenAction)
        assertFalse(PolkaswapCapability(paused, disclaimerAccepted = true).canOpenAction)
    }

    @Test
    fun stakingCapabilityExplainsUnsupportedAndWatchOnlyWallets() {
        val noAccount = buildStakingCapability(
            supportedNetworkIds = setOf("polkadot"),
            hasSupportedAccount = false,
            recoveryRequired = false,
            hasSigningMaterial = false,
            runtimeReady = true
        )
        val watchOnly = buildStakingCapability(
            supportedNetworkIds = setOf("polkadot"),
            hasSupportedAccount = true,
            recoveryRequired = false,
            hasSigningMaterial = false,
            runtimeReady = true
        )
        val available = buildStakingCapability(
            supportedNetworkIds = setOf("polkadot"),
            hasSupportedAccount = true,
            recoveryRequired = false,
            hasSigningMaterial = true,
            runtimeReady = true
        )

        assertEquals(DeFiAvailability.SetupRequired, noAccount.availability)
        assertTrue(noAccount.userFacingReason.orEmpty().contains("Substrate account"))
        assertEquals(DeFiAvailability.SetupRequired, watchOnly.availability)
        assertTrue(watchOnly.userFacingReason.orEmpty().contains("watch-only"))
        assertTrue(available.canOpenDestination)
        assertTrue(available.canPerformAction)
    }
}
