package jp.co.soramitsu.wallet.impl.domain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.isValidAddress
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

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
            recipientAddress == ownAddress -> TransferValidationResult.TransferToTheSameAddress
            else -> TransferValidationResult.Valid
        }
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val recipientAccountId = chain.accountIdOf(recipientAddress)

        val totalRecipientBalanceInPlanks = substrateSource.getTotalBalance(chainAsset, recipientAccountId)

        val result = when {
            chainAsset.type == ChainAssetType.Equilibrium -> {
                getEquilibriumValidationResult(asset, recipientAccountId, chain, ownAddress, amountInPlanks, fee, tip)
            }
            chainAsset.isUtility -> {
                val resultedBalance = (asset.freeInPlanks ?: transferable) - (amountInPlanks + fee + tip)
                when {
                    amountInPlanks + fee + tip > transferable -> TransferValidationResult.InsufficientBalance
                    resultedBalance < assetExistentialDeposit -> TransferValidationResult.ExistentialDepositWarning
                    totalRecipientBalanceInPlanks + amountInPlanks < assetExistentialDeposit -> TransferValidationResult.DeadRecipient
                    else -> TransferValidationResult.Valid
                }
            }
            else -> {
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
            }
        }
        return Result.success(result)
    }

    private suspend fun getEquilibriumValidationResult(
        asset: Asset,
        recipientAccountId: ByteArray,
        chain: Chain,
        ownAddress: String,
        amountInPlanks: BigInteger,
        fee: BigInteger,
        tip: BigInteger
    ): TransferValidationResult {
        val chainAsset = asset.token.configuration
        val assetExistentialDeposit = existentialDepositUseCase(chainAsset)

        val recipientAccountInfo = walletInteractor.getEquilibriumAccountInfo(chainAsset, recipientAccountId)
        val ownAccountInfo = walletInteractor.getEquilibriumAccountInfo(chainAsset, chain.accountIdOf(ownAddress))
        val assetRates = walletInteractor.getEquilibriumAssetRates(chainAsset)

        val ownNewTotal = ownAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
            val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

            val newAmount = when {
                chainAsset.isUtility -> {
                    if (eqAssetId == chainAsset.currency) {
                        if (amount < amountInPlanks + fee + tip) return TransferValidationResult.InsufficientBalance
                        amount - amountInPlanks - fee - tip
                    } else {
                        amount
                    }
                }
                eqAssetId == chainAsset.currency -> {
                    if (amount < amountInPlanks) return TransferValidationResult.InsufficientBalance
                    amount - amountInPlanks
                }
                eqAssetId == chain.utilityAsset.currency -> {
                    if (amount < fee + tip) return TransferValidationResult.InsufficientUtilityAssetBalance
                    amount - fee - tip
                }
                else -> amount
            }

            val amountDecimal = asset.token.configuration.amountFromPlanks(newAmount)
            val rateDecimal = asset.token.configuration.amountFromPlanks(tokenRateInPlanks)

            amountDecimal * rateDecimal
        }.orZero()

        val recipientNewTotal = recipientAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
            val newAmount = if (eqAssetId == chainAsset.currency) amount + amountInPlanks else amount
            val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

            val amountDecimal = asset.token.configuration.amountFromPlanks(newAmount)
            val rateDecimal = asset.token.configuration.amountFromPlanks(tokenRateInPlanks)

            amountDecimal * rateDecimal
        }.orZero()

        val recipientNewTotalInPlanks = asset.token.configuration.planksFromAmount(recipientNewTotal)
        val ownNewTotalInPlanks = asset.token.configuration.planksFromAmount(ownNewTotal)

        return when {
            ownNewTotalInPlanks < assetExistentialDeposit -> TransferValidationResult.ExistentialDepositWarning
            recipientNewTotalInPlanks < assetExistentialDeposit -> TransferValidationResult.DeadRecipient
            else -> TransferValidationResult.Valid
        }
    }
}
