package jp.co.soramitsu.crowdloan.impl.domain

import java.math.BigInteger
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanCallKind
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanEvidence
import jp.co.soramitsu.crowdloan.api.domain.classifyLegacyCrowdloanCall
import jp.co.soramitsu.crowdloan.api.domain.shouldShowLegacyCrowdloan
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCrowdloanRecoveryTest {

    @Test
    fun `polkadot utility asset is visible for a positive live contribution`() {
        val evidence = LegacyCrowdloanEvidence(
            currentContributionCount = 1,
            currentContributionAmount = BigInteger.TEN
        )

        assertTrue(shouldShowLegacyCrowdloan(polkadotChainId, "dot", "dot", evidence))
    }

    @Test
    fun `kusama utility asset is visible for a completed historical claim`() {
        val evidence = LegacyCrowdloanEvidence(historicalClaimCount = 1)

        assertTrue(shouldShowLegacyCrowdloan(kusamaChainId, "ksm", "ksm", evidence))
    }

    @Test
    fun `stale network metadata without account evidence stays hidden`() {
        assertFalse(
            shouldShowLegacyCrowdloan(
                chainId = polkadotChainId,
                chainAssetId = "dot",
                utilityAssetId = "dot",
                evidence = LegacyCrowdloanEvidence.None
            )
        )
    }

    @Test
    fun `evidence never leaks to another network or non utility asset`() {
        val evidence = LegacyCrowdloanEvidence(historicalLockCount = 1)

        assertFalse(shouldShowLegacyCrowdloan("another-chain", "dot", "dot", evidence))
        assertFalse(shouldShowLegacyCrowdloan(polkadotChainId, "usdt", "dot", evidence))
    }

    @Test
    fun `only completed-operation call shapes representing locks or claims classify`() {
        assertEquals(
            LegacyCrowdloanCallKind.LOCK,
            classifyLegacyCrowdloanCall("Crowdloan", "contribute")
        )
        assertEquals(
            LegacyCrowdloanCallKind.CLAIM,
            classifyLegacyCrowdloanCall("crowdloan", "withdraw")
        )
        assertNull(classifyLegacyCrowdloanCall("Crowdloan", "addMemo"))
        assertNull(classifyLegacyCrowdloanCall("Balances", "transfer"))
    }
}
