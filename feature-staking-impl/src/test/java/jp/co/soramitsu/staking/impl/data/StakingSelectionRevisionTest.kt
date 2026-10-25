package jp.co.soramitsu.staking.impl.data

import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StakingSelectionRevisionTest {
    @Test fun `presentation signal changes even when identical selection intents conflate`() {
        val revision = StakingSelectionRevision()
        val initial = revision.changes.value
        val selection = StakingAssetSelection.RelayChainStaking("chain", "asset")
        revision.changed(selection)
        val first = revision.changes.value
        revision.changed(selection)
        val second = revision.changes.value
        assertTrue(initial !== first && first !== second)
        assertTrue(second === revision.capture())
        assertFalse(revision.matchesAction(first, selection.type, selection.chainId, selection.chainAssetId))
        assertTrue(revision.matchesAction(second, selection.type, selection.chainId, selection.chainAssetId))
    }

    @Test fun `initial cached selection is usable before any explicit intent`() {
        val revision = StakingSelectionRevision()
        val snapshot = revision.capture()
        StakingType.entries.forEach { assertTrue(revision.matchesAction(snapshot, it, "chain", "asset")) }
    }

    @Test fun `every staking type accepts its exact requested chain and asset`() {
        val selections = listOf(
            StakingAssetSelection.RelayChainStaking("chain", "asset"),
            StakingAssetSelection.ParachainStaking("chain", "asset"),
            StakingAssetSelection.Pool("chain", "asset")
        )
        selections.forEach {
            val revision = StakingSelectionRevision()
            revision.changed(it)
            assertTrue(revision.matchesAction(revision.capture(), it.type, "chain", "asset"))
        }
    }

    @Test fun `same asset cannot authorize another scenario type`() {
        val revision = StakingSelectionRevision()
        revision.changed(StakingAssetSelection.Pool("chain", "asset"))
        assertFalse(revision.matchesAction(revision.capture(), StakingType.RELAYCHAIN, "chain", "asset"))
        assertFalse(revision.matchesAction(revision.capture(), StakingType.PARACHAIN, "chain", "asset"))
    }

    @Test fun `same scenario cannot authorize old chain or asset`() {
        val revision = StakingSelectionRevision()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain", "asset"))
        assertFalse(revision.matchesAction(revision.capture(), StakingType.RELAYCHAIN, "old-chain", "asset"))
        assertFalse(revision.matchesAction(revision.capture(), StakingType.RELAYCHAIN, "chain", "old-asset"))
    }

    @Test fun `return to the original selection cannot revive an earlier action`() {
        val revision = StakingSelectionRevision()
        val original = StakingAssetSelection.RelayChainStaking("chain", "asset")
        revision.changed(original)
        val snapshot = revision.capture()
        revision.changed(StakingAssetSelection.ParachainStaking("other", "asset"))
        revision.changed(original)
        assertFalse(revision.matchesAction(snapshot, original.type, original.chainId, original.chainAssetId))
        assertTrue(revision.matchesAction(revision.capture(), original.type, original.chainId, original.chainAssetId))
    }

    @Test fun `reselecting the identical asset invalidates an already captured action`() {
        val revision = StakingSelectionRevision()
        val selection = StakingAssetSelection.Pool("chain", "asset")
        revision.changed(selection)
        val snapshot = revision.capture()
        revision.changed(selection)
        assertFalse(revision.matchesAction(snapshot, selection.type, selection.chainId, selection.chainAssetId))
    }

    @Test fun `general action matching does not weaken pool preparation matching`() {
        val revision = StakingSelectionRevision()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain", "asset"))
        val snapshot = revision.capture()
        assertTrue(revision.matchesAction(snapshot, StakingType.RELAYCHAIN, "chain", "asset"))
        assertFalse(revision.matches(snapshot, "chain", "asset"))
        revision.changed(StakingAssetSelection.Pool("chain", "asset"))
        assertTrue(revision.matches(revision.capture(), "chain", "asset"))
    }
}
