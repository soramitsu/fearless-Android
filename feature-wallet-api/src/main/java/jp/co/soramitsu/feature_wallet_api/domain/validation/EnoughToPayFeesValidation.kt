package jp.co.soramitsu.feature_wallet_api.domain.validation

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.chain
import java.math.BigDecimal

class EnoughToPayFeesValidation<P, E>(
    private val feeExtractor: AmountProducer<P>,
    private val availableBalanceProducer: AmountProducer<P>,
    private val errorProducer: () -> E,
    private val extraAmountExtractor: AmountProducer<P> = { BigDecimal.ZERO },
) : Validation<P, E> {

    companion object;

    override suspend fun validate(value: P): ValidationStatus<E> {

        return if (extraAmountExtractor(value) + feeExtractor(value) < availableBalanceProducer(value)) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}

fun <P> EnoughToPayFeesValidation.Companion.assetBalanceProducer(
    walletRepository: WalletRepository,
    stakingSharedState: SingleAssetSharedState,
    originAddressExtractor: (P) -> String,
    chainAssetExtractor: (P) -> Chain.Asset,
): AmountProducer<P> = { payload ->
    val chain = stakingSharedState.chain()
    val accountId = chain.accountIdOf(originAddressExtractor(payload))

    val asset = walletRepository.getAsset(accountId, chainAssetExtractor(payload))!!

    asset.transferable
}
