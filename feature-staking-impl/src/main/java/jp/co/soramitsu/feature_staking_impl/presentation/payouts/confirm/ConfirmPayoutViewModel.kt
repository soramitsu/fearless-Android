package jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable

class ConfirmPayoutViewModel(
    private val interactor: StakingInteractor,
    private val router: StakingRouter,
    private val payouts: List<PendingPayoutParcelable>,
    private val addressModelGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val validationSystem: ValidationSystem<MakePayoutPayload, PayoutValidationFailure>,
    private val resourceManager: ResourceManager,
) : BaseViewModel(),
    ExternalAccountActions.Presentation by externalAccountActions,
    FeeLoaderMixin by feeLoaderMixin
