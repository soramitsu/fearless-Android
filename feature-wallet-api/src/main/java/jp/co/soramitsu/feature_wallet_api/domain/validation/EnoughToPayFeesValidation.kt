package jp.co.soramitsu.feature_wallet_api.domain.validation

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
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
    originAddressExtractor: (P) -> String,
    tokenTypeExtractor: (P) -> Token.Type,
): AmountProducer<P> = { payload ->
    val asset = walletRepository.getAsset(originAddressExtractor(payload), tokenTypeExtractor(payload))!!

    asset.transferable
}
