package jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination

import androidx.lifecycle.LiveData
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface RewardDestinationMixin : Browserable {

    val rewardReturnsLiveData: LiveData<RewardDestinationEstimations>

    val showDestinationChooserEvent: LiveData<Event<DynamicListBottomSheet.Payload<AddressModel>>>

    val rewardDestinationModelFlow: Flow<RewardDestinationModel>

    fun payoutClicked(scope: CoroutineScope)

    fun payoutTargetClicked(scope: CoroutineScope)

    fun payoutDestinationChanged(newDestination: AddressModel)

    fun learnMoreClicked()

    fun restakeClicked()

    interface Presentation : RewardDestinationMixin {

        val rewardDestinationChangedFlow: Flow<Boolean>

        suspend fun loadActiveRewardDestination(stashState: StakingState.Stash)

        suspend fun updateReturns(
            rewardCalculator: RewardCalculator,
            asset: Asset,
            amount: BigDecimal,
        )
    }
}
