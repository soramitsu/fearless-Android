package jp.co.soramitsu.staking.impl.presentation.payouts.detail

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class PayoutDetailsViewModel @Inject constructor(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val addressModelGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val resourceManager: ResourceManager,
    private val savedStateHandle: SavedStateHandle,
    private val soraRewardScenario: SoraStakingRewardsScenario
) : BaseViewModel(), ExternalAccountActions.Presentation by externalAccountActions {

    private val payout =
        savedStateHandle.get<PendingPayoutParcelable>(PayoutDetailsFragment.KEY_PAYOUT)!!

    private val assetFlow = interactor.currentAssetFlow()

    val payoutDetails = assetFlow
        .map(::mapPayoutParcelableToPayoutDetailsModel)
        .inBackground()
        .asLiveData()

    fun backClicked() {
        router.back()
    }

    fun payoutClicked() {
        val payload = ConfirmPayoutPayload(
            totalRewardInPlanks = payout.amountInPlanks,
            payouts = listOf(payout)
        )

        router.openConfirmPayout(payload)
    }

    fun validatorExternalActionClicked() = launch {
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedAddressExplorers(payout.validatorInfo.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = payout.validatorInfo.address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private suspend fun mapPayoutParcelableToPayoutDetailsModel(asset: Asset): PayoutDetailsModel {
        val tokenType =
            if (asset.token.configuration.syntheticStakingType() == SyntheticStakingType.SORA) {
                soraRewardScenario.getRewardAsset().configuration
            } else asset.token.configuration

        val rewardAmount = asset.token.amountFromPlanks(payout.amountInPlanks)

        val addressModel = with(payout.validatorInfo) {
            addressModelGenerator.createAddressModel(
                address,
                AddressIconGenerator.SIZE_SMALL,
                identityName
            )
        }

        return PayoutDetailsModel(
            validatorAddressModel = addressModel,
            createdAt = payout.createdAt,
            eraDisplay = resourceManager.getString(
                R.string.staking_era_index_no_prefix,
                payout.era.toLong()
            ),
            reward = rewardAmount.formatCryptoDetail(tokenType.symbol),
            rewardFiat = asset.token.fiatAmount(rewardAmount)?.formatFiat(asset.token.fiatSymbol)
        )
    }
}
