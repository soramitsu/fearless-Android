package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigInteger

class TonValidationChecksProvider(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository
) : ValidationChecksProvider {

    override suspend fun provide(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationAddress: String,
        fee: BigInteger
    ): Map<TransferValidationResult, Boolean> {
        val chainId = asset.token.configuration.chainId
        val chain = chainsRepository.getChain(chainId)

        val metaAccount = accountRepository.getSelectedMetaAccount()

        val accountId = metaAccount.accountId(chain)!!

        val utilityAsset = chain.utilityAsset?.let {
            walletRepository.getAsset(
                metaAccount.id,
                accountId,
                it,
                chain.minSupportedVersion
            )
        }

        requireNotNull(utilityAsset)

        val utilityAssetBalance = utilityAsset.transferableInPlanks.orZero()

        val throwInsufficientBalanceError = if (asset.token.configuration.isUtility) {
            amountInPlanks + fee > asset.transferableInPlanks
        } else {
            amountInPlanks > asset.transferableInPlanks
        }

        return mapOf(
            TransferValidationResult.InsufficientBalance to (throwInsufficientBalanceError),
            TransferValidationResult.InsufficientUtilityAssetBalance to (fee > utilityAssetBalance)
        )
    }
}