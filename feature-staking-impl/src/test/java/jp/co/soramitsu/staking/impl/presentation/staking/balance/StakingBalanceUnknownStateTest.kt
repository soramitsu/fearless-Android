package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as ChainAsset
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.model.AmountModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StakingBalanceUnknownStateTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    @Test fun eachUnknownComponentBlocksStakeAndUnstakeUntilKnownValuesRecover() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val config = mock<ChainAsset> {
            on { chainId } doReturn "fixture"
            on { id } doReturn "native"
            on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
            on { precision } doReturn 10
        }
        val asset = Asset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
        val balances = MutableStateFlow(model(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE))
        val scenario = mock<StakingScenarioInteractor> {
            on { getStakingBalanceFlow(anyOrNull()) } doReturn balances
            onBlocking { currentUnbondingsFlow(anyOrNull()) } doReturn flowOf(emptyList())
            onBlocking { getRebondingUnbondings(anyOrNull()) } doReturn emptyList()
        }
        val interactor = mock<StakingInteractor> { on { currentAssetFlow() } doReturn flowOf(asset) }
        val vm = StakingBalanceViewModel(mock(), mock(), mock(), mock(), interactor, scenario, SavedStateHandle())
        val observer = Observer<Boolean> { }
        try {
            vm.shouldBlockStakeMore.observeForever(observer)
            vm.shouldBlockUnstake.observeForever(observer)
            vm.redeemEnabledLiveData.observeForever(observer)
            runCurrent()
            assertEquals(false, vm.shouldBlockStakeMore.value)
            assertEquals(false, vm.shouldBlockUnstake.value)
            assertEquals(true, vm.redeemEnabledLiveData.value)
            for (unknown in listOf(
                model(null, BigDecimal.ZERO, BigDecimal.ONE),
                model(BigDecimal.TEN, null, BigDecimal.ONE),
                model(BigDecimal.TEN, BigDecimal.ZERO, null)
            )) {
                balances.value = unknown
                runCurrent()
                assertEquals(true, vm.shouldBlockStakeMore.value)
                assertEquals(true, vm.shouldBlockUnstake.value)
            }
            assertEquals(false, vm.redeemEnabledLiveData.value)
            balances.value = model(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)
            runCurrent()
            assertEquals(false, vm.shouldBlockStakeMore.value)
            assertEquals(false, vm.shouldBlockUnstake.value)
            assertEquals(false, vm.redeemEnabledLiveData.value)
        } finally {
            vm.shouldBlockStakeMore.removeObserver(observer)
            vm.shouldBlockUnstake.removeObserver(observer)
            vm.redeemEnabledLiveData.removeObserver(observer)
            vm.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private fun model(staked: BigDecimal?, unstaking: BigDecimal?, redeemable: BigDecimal?) = StakingBalanceModel(
        AmountModel(staked, staked?.toString() ?: "unavailable", null),
        AmountModel(unstaking, unstaking?.toString() ?: "unavailable", null),
        AmountModel(redeemable, redeemable?.toString() ?: "unavailable", null)
    )
}
