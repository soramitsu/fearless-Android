package jp.co.soramitsu.featurestakingimpl.presentation.common.rewardDestination

import androidx.lifecycle.LiveData
import java.math.BigDecimal
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurestakingimpl.domain.rewards.RewardCalculator
import jp.co.soramitsu.featurewalletapi.domain.model.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface RewardDestinationMixin : Browserable {

    val rewardReturnsLiveData: LiveData<RewardDestinationEstimations>

    val showDestinationChooserEvent: LiveData<Event<DynamicListBottomSheet.Payload<AddressModel>>>

    val rewardDestinationModelFlow: Flow<RewardDestinationModel>

    fun payoutClicked(scope: CoroutineScope)

    fun payoutTargetClicked(scope: CoroutineScope)

    fun payoutDestinationChanged(newDestination: AddressModel, scope: CoroutineScope)

    fun learnMoreClicked(scope: CoroutineScope)

    fun restakeClicked(scope: CoroutineScope)

    interface Presentation : RewardDestinationMixin {

        val rewardDestinationChangedFlow: Flow<Boolean>

        suspend fun loadActiveRewardDestination(stashState: StakingState.Stash)

        suspend fun updateReturns(
            rewardCalculator: RewardCalculator,
            asset: Asset,
            amount: BigDecimal
        )
    }
}
