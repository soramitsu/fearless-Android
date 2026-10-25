package jp.co.soramitsu.staking.impl.presentation.common

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual suspended context-preparation path with public fixture metadata and no signing/network. */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingPoolContextPreparationTest {
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val chainAsset = mock<ChainAsset> {
        on { chainId } doReturn "local-pool-chain"
        on { id } doReturn "native"
        on { symbol } doReturn "LOCAL"
        on { precision } doReturn 10
    }
    private val chain = mock<Chain> {
        on { id } doReturn "local-pool-chain"
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
    }
    private val asset = Asset.createEmpty(chainAsset, 7, account, minSupportedVersion = null)
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val selectionRevision = StakingSelectionRevision()
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn this@StakingPoolContextPreparationTest.selectionRevision
        on { currentAssetFlow() } doReturn MutableStateFlow(asset)
        on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
    }
    private val interactor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doReturn meta
    }

    @Test fun `coherent preparation retains the exact asset sender amount and stable wallet epoch`() = runTest {
        val epoch = WalletSelectionEpoch.snapshot()
        val state = prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE }
        assertNotNull(state)
        assertSame(asset, state!!.asset)
        assertSame(chain, state.chain)
        assertEquals(BigDecimal.ONE, state.amount)
        assertEquals(epoch, state.walletSelectionEpoch)
        assertTrue(state.accountId.contentEquals(account))
    }

    @Test fun `ongoing selection refuses preparation before reading wallet metadata`() = runTest {
        WalletSelectionEpoch.beginChange()
        try {
            assertNull(prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE })
            verify(selected, never()).currentAssetFlow()
            verify(interactor, never()).getCurrentMetaAccount()
        } finally { WalletSelectionEpoch.endChange() }
    }

    @Test fun `wallet change during metadata suspension cannot publish a fresh epoch over old assets`() = runTest {
        val held = CompletableDeferred<MetaAccount>()
        whenever(interactor.getCurrentMetaAccount()).doSuspendableAnswer { held.await() }
        val prepared = async { prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE } }
        runCurrent()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        held.complete(meta)
        assertNull(prepared.await())
    }

    @Test fun `wallet change during final amount suspension invalidates all earlier reads`() = runTest {
        val held = CompletableDeferred<BigDecimal>()
        val prepared = async { prepareStakingPoolContext(selected, interactor) { held.await() } }
        runCurrent()
        WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange()
        held.complete(BigDecimal.ONE)
        assertNull(prepared.await())
    }

    @Test fun `cached balance belonging to another wallet cannot form the join context`() = runTest {
        val otherWallet = asset.copy(metaId = 8)
        whenever(selected.currentAssetFlow()).thenReturn(MutableStateFlow(otherWallet))
        assertNull(prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE })
    }

    @Test fun `cached account belonging to another sender cannot form the join context`() = runTest {
        val otherAccount = asset.copy(accountId = ByteArray(32) { 9 })
        whenever(selected.currentAssetFlow()).thenReturn(MutableStateFlow(otherAccount))
        assertNull(prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE })
    }

    @Test fun `asset and selected network mismatch cannot form the join context`() = runTest {
        whenever(chain.id).thenReturn("different-local-chain")
        assertNull(prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE })
    }

    @Test fun `asset and selected token mismatch cannot form the join context`() = runTest {
        val other = mock<ChainAsset> { on { id } doReturn "other-token" }
        whenever(selected.assetWithChain).thenReturn(flowOf(SingleAssetSharedState.AssetWithChain(chain, other)))
        assertNull(prepareStakingPoolContext(selected, interactor) { BigDecimal.ONE })
    }
}
