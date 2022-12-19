package jp.co.soramitsu.wallet.impl.domain.validation

import java.math.BigDecimal
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.Validation
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository

class EnoughToPayFeesValidation<P, E>(
    private val feeExtractor: AmountProducer<P>,
    private val availableBalanceProducer: AmountProducer<P>,
    private val errorProducer: () -> E,
    private val extraAmountExtractor: AmountProducer<P> = { BigDecimal.ZERO }
) : Validation<P, E> {

    companion object;

    override suspend fun validate(value: P): ValidationStatus<E> {
        val result = if (extraAmountExtractor(value) + feeExtractor(value) < availableBalanceProducer(value)) {
            ValidationStatus.Valid()
        } else {
            ValidationStatus.NotValid(DefaultFailureLevel.ERROR, errorProducer())
        }
        return result
    }
}

fun <P> EnoughToPayFeesValidation.Companion.assetBalanceProducer(
    accountRepository: AccountRepository,
    walletRepository: WalletRepository,
    chainProducer: suspend () -> Chain,
    originAddressExtractor: (P) -> String,
    chainAssetExtractor: (P) -> Chain.Asset
): AmountProducer<P> = { payload ->
    val chain = chainProducer()
    val accountId = chain.accountIdOf(originAddressExtractor(payload))
    val meta = accountRepository.getSelectedMetaAccount()

    val asset = walletRepository.getAsset(meta.id, accountId, chainAssetExtractor(payload), chain.minSupportedVersion)!!

    asset.availableForStaking
}
