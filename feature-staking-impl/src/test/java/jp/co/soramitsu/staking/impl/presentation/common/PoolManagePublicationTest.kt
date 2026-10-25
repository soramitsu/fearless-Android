package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigInteger
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import org.junit.Assert.*
import org.junit.Test

class PoolManagePublicationTest {
    private fun provider() = StakingPoolSharedStateProvider().also { it.mainState.set(StakingPoolState()) }
    private fun fields(p: StakingPoolSharedStateProvider) = StakingPoolManageFlowState(
        BigInteger.ONE, BigInteger.TEN, BigInteger.TEN, context = p.mainState.capture()
    )

    @Test fun `current management publication preserves original context`() {
        val p = provider(); val f = fields(p)
        assertTrue(p.publishManageState(f.context, f)); assertSame(f, p.manageState.get())
    }
    @Test fun `departed observer cannot overwrite newer management fields`() {
        val p = provider(); val old = fields(p); p.mainState.set(StakingPoolState())
        val current = fields(p); assertTrue(p.publishManageState(current.context, current))
        assertFalse(p.publishManageState(old.context, old)); assertSame(current, p.manageState.get())
    }
    @Test fun `wallet round trip revokes management publisher`() {
        val p = provider(); val old = fields(p)
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        assertFalse(p.publishManageState(old.context, old)); assertNull(p.manageState.get())
    }
    @Test fun `refresh cannot replace active claim and consumed fields cannot be reclaimed or edited`() {
        val p = provider(); val f = fields(p); assertTrue(p.publishManageState(f.context, f))
        val draft = p.manageState.capture(); val claim = requireNotNull(p.manageState.tryClaim(draft))
        assertFalse(p.publishManageState(f.context, f.copy(claimableInPlanks = BigInteger.TWO)))
        val completion = requireNotNull(p.manageState.consumeKeepingValue(claim))
        assertSame(f, completion.value); assertNull(p.manageState.tryClaim(completion))
        assertFalse(p.manageState.isEditable(completion)); assertFalse(p.manageState.setIfCurrent(completion, f))
        p.manageState.release(claim); assertNull(p.manageState.tryClaim(completion))
        assertTrue(p.publishManageState(f.context, f.copy(claimableInPlanks = BigInteger.TWO)))
        assertNotNull(p.manageState.tryClaim(p.manageState.capture()))
    }
    @Test fun `old result cannot consume replacement draft`() {
        val p = provider(); val f = fields(p); p.manageState.set(f)
        val claim = requireNotNull(p.manageState.tryClaim(p.manageState.capture()))
        val newer = f.copy(claimableInPlanks = BigInteger.TWO); p.manageState.set(newer)
        assertNull(p.manageState.consumeKeepingValue(claim)); assertSame(newer, p.manageState.get())
    }
}
