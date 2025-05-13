package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigInteger

class EthereumValidationChecksProvider(
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletRepository: WalletRepository
): ValidationChecksProvider {
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
        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)
        val edFormatted = existentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration)

        val utilityAsset = chain.utilityAsset?.let {
            walletRepository.getAsset(
                metaAccount.id,
                accountId,
                it,
                chain.minSupportedVersion
            )
        }
        val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
        val resultedBalance =
            (asset.freeInPlanks.positiveOrNull().orZero()) - (amountInPlanks + fee)

        return mapOf(
            TransferValidationResult.InsufficientBalance to (amountInPlanks + fee > asset.transferableInPlanks),
            TransferValidationResult.ExistentialDepositWarning(edFormatted) to (resultedBalance < existentialDeposit),
            TransferValidationResult.InsufficientUtilityAssetBalance to (fee > utilityAssetBalance)
        )
    }
}