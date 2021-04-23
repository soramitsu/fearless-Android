package jp.co.soramitsu.feature_staking_impl.domain.validations

import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.validation.AmountProducer
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import kotlinx.coroutines.flow.first

fun <P> EnoughToPayFeesValidation.Companion.storageControllerBalanceProducer(
    stakingRepository: StakingRepository,
    originAddressExtractor: (P) -> String,
    tokenTypeExtractor: (P) -> Token.Type,
): AmountProducer<P> = { payload ->

    val accountAddress = originAddressExtractor(payload)
    val stakingState = stakingRepository.stakingStateFlow(accountAddress).first()

    require(stakingState is StakingState.Stash) { "Account is not stash" }

    val accountInfo = stakingRepository.getControllerAccountInfo(stakingState)
    val token = tokenTypeExtractor(payload)

    token.amountFromPlanks(accountInfo.data.free)
}
