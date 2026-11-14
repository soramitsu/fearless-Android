package jp.co.soramitsu.staking.impl.domain.validators.contract

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.validators.CollatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.staking.impl.domain.validators.current.search.SearchCustomBlockProducerInteractor
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

internal class ProducerSearchFixture(parachain: Boolean = false) {
    val relay = ValidatorCatalogueFixture()
    val para = CollatorCatalogueFixture()
    val validators = listOf(relay.validator(), relay.validator(1))
    val collators = linkedMapOf(para.hex[0] to para.collator(), para.hex[1] to para.collator(1))
    val chain = if (parachain) para.chain else relay.chain
    val asset = if (parachain) para.asset else relay.asset
    val selection = MutableStateFlow(SingleAssetSharedState.AssetWithChain(chain, asset))
    val shared = mock<StakingSharedState> { on { assetWithChain } doReturn selection }
    val validatorProvider = mock<ValidatorProvider>()
    val collatorProvider = mock<CollatorProvider>()
    val accounts = mock<AccountRepository>()
    val wallets = MutableStateFlow(para.wallet)
    val cache = ComputationalCache()
    val lifecycle = SearchContractLifecycle()
    val process = SetupStakingSharedState()
    val destination = RewardDestination.Payout(byteArrayOf(7))
    val interactor = SearchCustomBlockProducerInteractor(collatorProvider, validatorProvider, shared, cache, mock(), accounts)

    suspend fun defaults() {
        whenever(accounts.getSelectedMetaAccount()).thenAnswer { wallets.value }
        whenever(accounts.selectedMetaAccountFlow()).thenReturn(wallets)
        whenever(shared.chain()).thenAnswer { selection.value.chain }
        whenever(validatorProvider.getValidators(any(), any())).thenReturn(validators)
        whenever(validatorProvider.getValidatorWithoutElectedInfo(any(), any())).thenReturn(validators[0])
        whenever(collatorProvider.getCollators(any(), anyOrNull())).thenReturn(collators)
        if (asset.staking == Asset.StakingType.PARACHAIN) {
            process.setupStakingProcess.value = SetupStakingProcess.SelectBlockProducersStep.Collators(
                SetupStakingProcess.SelectBlockProducersStep.Payload.Parachain(BigDecimal("12.34"), "delegator")
            ).next(listOf(para.collator(1)), SetupStakingProcess.ReadyToSubmit.SelectionMethod.RECOMMENDED)
        } else {
            readyRelay(listOf(validators[0]))
        }
    }

    fun readyRelay(selected: List<Validator>) {
        process.setupStakingProcess.value = SetupStakingProcess.SelectBlockProducersStep.Validators(
            SetupStakingProcess.SelectBlockProducersStep.Payload.Relaychain(BigDecimal("12.34"), destination, "controller")
        ).next(selected, SetupStakingProcess.ReadyToSubmit.SelectionMethod.RECOMMENDED)
    }

    suspend fun rows() = interactor.getBlockProducers(lifecycle)
    suspend fun choose(address: String) = interactor.blockProducerSelected(address, process, lifecycle)
    fun relayPayload() = process.get<SetupStakingProcess.ReadyToSubmit.Stash>().payload
    fun paraPayload() = process.get<SetupStakingProcess.ReadyToSubmit.Parachain>().payload
}
