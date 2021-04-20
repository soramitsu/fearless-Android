package jp.co.soramitsu.feature_wallet_api.domain.validation

import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class EnoughToPayFeesValidation<T, E>(
    private val walletRepository: WalletRepository,
    private val feeExtractor: (T) -> BigDecimal,
    private val originAddressExtractor: (T) -> String,
    private val tokenTypeExtractor: (T) -> Token.Type,
    private val errorProducer: () -> E,
    private val extraAmountExtractor: (T) -> BigDecimal = { BigDecimal.ZERO },
) : Validation<T, E> {

    companion object;

    override suspend fun validate(value: T): ValidationStatus<E> {
        val asset = walletRepository.getAsset(originAddressExtractor(value), tokenTypeExtractor(value))!!

        return if (extraAmountExtractor(value) + feeExtractor(value) < asset.transferable) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
    }
}
