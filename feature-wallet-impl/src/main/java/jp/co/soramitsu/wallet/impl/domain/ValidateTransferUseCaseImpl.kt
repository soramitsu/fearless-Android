package jp.co.soramitsu.wallet.impl.domain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset

class ValidateTransferUseCaseImpl(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val chainRegistry: ChainRegistry,
    private val walletInteractor: WalletInteractor,
    private val substrateSource: SubstrateRemoteSource
) : ValidateTransferUseCase {

    override suspend fun invoke(
        amountInPlanks: BigInteger,
        asset: Asset,
        recipientAddress: String,
        ownAddress: String,
        fee: BigInteger?
    ): Result<TransferValidationResult> = kotlin.runCatching {
        fee ?: return Result.success(TransferValidationResult.WaitForFee)
        val chainId = asset.token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val chainAsset = asset.token.configuration
        val transferable = asset.transferableInPlanks
        val assetExistentialDeposit = existentialDepositUseCase(chainAsset)
        val tip = if (chainAsset.isUtility) walletConstants.tip(chainId).orZero() else BigInteger.ZERO

        val validateAddressResult = kotlin.runCatching { chain.isValidAddress(recipientAddress) }
        val initialCheck = when {
            validateAddressResult.isFailure -> TransferValidationResult.InvalidAddress
            validateAddressResult.getOrNull() == false -> TransferValidationResult.InvalidAddress
            else -> TransferValidationResult.Valid
        }
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val recipientAccountId = chain.accountIdOf(recipientAddress)

        val totalRecipientBalanceInPlanks = substrateSource.getTotalBalance(chainAsset, recipientAccountId)

        val result = if (chainAsset.type == ChainAssetType.SoraAsset) {
            val utilityAsset = walletInteractor.getCurrentAsset(chainId, chain.utilityAsset.id)
            val utilityAssetBalance = utilityAsset.transferableInPlanks
            val utilityAssetExistentialDeposit = existentialDepositUseCase(chain.utilityAsset)

            when {
                amountInPlanks > transferable -> TransferValidationResult.InsufficientBalance
                fee + tip > utilityAssetBalance -> TransferValidationResult.InsufficientUtilityAssetBalance
                transferable - amountInPlanks < assetExistentialDeposit -> TransferValidationResult.ExistentialDepositWarning
                utilityAssetBalance - (fee + tip) < utilityAssetExistentialDeposit -> TransferValidationResult.UtilityExistentialDepositWarning
                totalRecipientBalanceInPlanks + amountInPlanks < assetExistentialDeposit -> TransferValidationResult.DeadRecipient
                else -> TransferValidationResult.Valid
            }
        } else {
            val resultedBalance = (asset.freeInPlanks ?: transferable) - (amountInPlanks + fee + tip)
            when {
                amountInPlanks + fee + tip > transferable -> TransferValidationResult.InsufficientBalance
                resultedBalance < assetExistentialDeposit -> TransferValidationResult.ExistentialDepositWarning
                totalRecipientBalanceInPlanks + amountInPlanks < assetExistentialDeposit -> TransferValidationResult.DeadRecipient
                else -> TransferValidationResult.Valid
            }
        }
        return Result.success(result)
    }
}
