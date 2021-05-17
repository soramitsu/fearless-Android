package jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.rewards.DAYS_IN_YEAR
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.RewardSuffix
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.setup.AccountChooserBottomSheetDialog
import jp.co.soramitsu.feature_staking_impl.presentation.setup.PayoutEstimations
import jp.co.soramitsu.feature_staking_impl.presentation.setup.RewardDestinationModel
import jp.co.soramitsu.feature_staking_impl.presentation.view.RewardDestinationChooserView
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayout
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserPayoutTarget
import kotlinx.android.synthetic.main.view_reward_destination_chooser.view.rewardDestinationChooserRestake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

interface RewardDestinationMixin : Browserable {

    val rewardReturnsLiveData: LiveData<PayoutEstimations>

    val showDestinationChooserEvent: LiveData<Event<DynamicListBottomSheet.Payload<AddressModel>>>

    val rewardDestinationFlow: Flow<RewardDestinationModel>

    fun payoutClicked(scope: CoroutineScope)

    fun payoutTargetClicked(scope: CoroutineScope)

    fun payoutDestinationChanged(newDestination: AddressModel)

    fun learnMoreClicked()

    fun restakeClicked()

    interface Presentation : RewardDestinationMixin {

        suspend fun updateReturns(
            rewardCalculator: RewardCalculator,
            asset: Asset,
            amount: BigDecimal,
        )
    }
}

class RewardDestinationProvider(
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val appLinksProvider: AppLinksProvider,
) : RewardDestinationMixin.Presentation {

    override val rewardReturnsLiveData = MutableLiveData<PayoutEstimations>()
    override val showDestinationChooserEvent = MutableLiveData<Event<DynamicListBottomSheet.Payload<AddressModel>>>()

    override val rewardDestinationFlow = MutableStateFlow<RewardDestinationModel>(RewardDestinationModel.Restake)

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    override fun payoutClicked(scope: CoroutineScope) {
        scope.launch {
            val currentAccount = interactor.getSelectedAccount()

            rewardDestinationFlow.value = RewardDestinationModel.Payout(generateDestinationModel(currentAccount))
        }
    }

    override fun payoutTargetClicked(scope: CoroutineScope) {
        val selectedDestination = rewardDestinationFlow.value as? RewardDestinationModel.Payout ?: return

        scope.launch {
            val accountsInNetwork = accountsInCurrentNetwork()

            showDestinationChooserEvent.value = Event(DynamicListBottomSheet.Payload(accountsInNetwork, selectedDestination.destination))
        }
    }

    override fun payoutDestinationChanged(newDestination: AddressModel) {
        rewardDestinationFlow.value = RewardDestinationModel.Payout(newDestination)
    }

    override fun learnMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.payoutsLearnMore)
    }

    override fun restakeClicked() {
        rewardDestinationFlow.value = RewardDestinationModel.Restake
    }

    override suspend fun updateReturns(rewardCalculator: RewardCalculator, asset: Asset, amount: BigDecimal) {
        val restakeReturns = rewardCalculator.calculateReturns(amount, DAYS_IN_YEAR, true)
        val payoutReturns = rewardCalculator.calculateReturns(amount, DAYS_IN_YEAR, false)

        val restakeEstimations = mapPeriodReturnsToRewardEstimation(restakeReturns, asset.token, resourceManager, RewardSuffix.APY)
        val payoutEstimations = mapPeriodReturnsToRewardEstimation(payoutReturns, asset.token, resourceManager, RewardSuffix.APR)

        rewardReturnsLiveData.value = PayoutEstimations(restakeEstimations, payoutEstimations)
    }

    private suspend fun accountsInCurrentNetwork(): List<AddressModel> {
        return interactor.getAccountsInCurrentNetwork()
            .map { generateDestinationModel(it) }
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, AddressIconGenerator.SIZE_MEDIUM, account.name)
    }
}

fun <V> BaseFragment<V>.observeRewardDestinationChooser(
    viewModel: V,
    chooser: RewardDestinationChooserView,
) where V : BaseViewModel, V : RewardDestinationMixin {
    viewModel.rewardDestinationFlow.observe {
        chooser.rewardDestinationChooserPayoutTarget.setVisible(it is RewardDestinationModel.Payout)
        chooser.rewardDestinationChooserRestake.isChecked = it is RewardDestinationModel.Restake
        chooser.rewardDestinationChooserPayout.isChecked = it is RewardDestinationModel.Payout

        if (it is RewardDestinationModel.Payout) {
            chooser.rewardDestinationChooserPayoutTarget.setMessage(it.destination.nameOrAddress)
            chooser.rewardDestinationChooserPayoutTarget.setTextIcon(it.destination.image)
        }
    }

    viewModel.rewardReturnsLiveData.observe {
        chooser.rewardDestinationChooserPayout.setPercentageGain(it.payout.gain)
        chooser.rewardDestinationChooserPayout.setTokenAmount(it.payout.amount)
        chooser.rewardDestinationChooserPayout.setFiatAmount(it.payout.fiatAmount)

        chooser.rewardDestinationChooserRestake.setPercentageGain(it.restake.gain)
        chooser.rewardDestinationChooserRestake.setTokenAmount(it.restake.amount)
        chooser.rewardDestinationChooserRestake.setFiatAmount(it.restake.fiatAmount)
    }

    viewModel.showDestinationChooserEvent.observeEvent {
        AccountChooserBottomSheetDialog(
            requireContext(),
            it,
            viewModel::payoutDestinationChanged,
            R.string.staking_setup_reward_payout_account
        ).show()
    }

    chooser.destinationPayout.setOnClickListener { viewModel.payoutClicked(viewModel) }
    chooser.destinationRestake.setOnClickListener { viewModel.restakeClicked() }
    chooser.payoutTarget.setWholeClickListener { viewModel.payoutTargetClicked(viewModel) }
    chooser.learnMore.setOnClickListener { viewModel.learnMoreClicked() }
}
