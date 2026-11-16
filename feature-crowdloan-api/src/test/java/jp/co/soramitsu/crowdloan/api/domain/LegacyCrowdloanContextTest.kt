package jp.co.soramitsu.crowdloan.api.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class LegacyCrowdloanContextTest {

    @Test fun anyLockOrClaimCountsAsEvidence() {
        assertFalse(LegacyCrowdloanEvidence.None.hasLockOrClaim)
        assertTrue(LegacyCrowdloanEvidence(currentContributionCount = 1).hasLockOrClaim)
        assertTrue(LegacyCrowdloanEvidence(currentContributionAmount = BigInteger.ONE).hasLockOrClaim)
        assertTrue(LegacyCrowdloanEvidence(historicalLockCount = 1).hasLockOrClaim)
        assertTrue(LegacyCrowdloanEvidence(historicalClaimCount = 1).hasLockOrClaim)
        assertFalse(LegacyCrowdloanEvidence(latestHistoricalActivityAt = 1_700_000_000_000).hasLockOrClaim)
    }

    @Test fun onlyRelayChainUtilityAssetsAreCrowdloanContexts() {
        assertTrue(isLegacyCrowdloanContext(polkadotChainId, "dot", "dot"))
        assertTrue(isLegacyCrowdloanContext(kusamaChainId, "ksm", "ksm"))
        assertFalse(isLegacyCrowdloanContext(kusamaChainId, "usdt", "ksm"))
        assertFalse(isLegacyCrowdloanContext(polkadotChainId, "dot", null))
        assertFalse(isLegacyCrowdloanContext("westend", "wnd", "wnd"))
    }

    @Test fun crowdloanEntryNeedsBothContextAndEvidence() {
        val evidence = LegacyCrowdloanEvidence(historicalLockCount = 2)

        assertTrue(shouldShowLegacyCrowdloan(kusamaChainId, "ksm", "ksm", evidence))
        assertFalse(shouldShowLegacyCrowdloan(kusamaChainId, "ksm", "ksm", LegacyCrowdloanEvidence.None))
        assertFalse(shouldShowLegacyCrowdloan("westend", "wnd", "wnd", evidence))
    }

    @Test fun lockAndClaimCallsAreRecognisedInAnySpelling() {
        assertEquals(LegacyCrowdloanCallKind.LOCK, classifyLegacyCrowdloanCall("CROWDLOAN", "contribute_all"))
        assertEquals(LegacyCrowdloanCallKind.LOCK, classifyLegacyCrowdloanCall("crowdloan", "contributeAll"))
        assertEquals(LegacyCrowdloanCallKind.CLAIM, classifyLegacyCrowdloanCall("Crowdloan", "refund"))
        assertEquals(LegacyCrowdloanCallKind.CLAIM, classifyLegacyCrowdloanCall("Crowdloan", "Claim"))
        assertNull(classifyLegacyCrowdloanCall("Crowdloan", null))
        assertNull(classifyLegacyCrowdloanCall(null, "contribute"))
        assertNull(classifyLegacyCrowdloanCall("Crowdloan", "dissolve"))
    }
}
