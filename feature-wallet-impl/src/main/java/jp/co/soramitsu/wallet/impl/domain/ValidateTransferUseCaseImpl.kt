package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.isSoraBasedChain
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.FEE_RESERVE_TOLERANCE
import java.math.BigDecimal
import java.math.BigInteger

class ValidateTransferUseCaseImpl(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val polkaswapInteractor: PolkaswapInteractor
) : ValidateTransferUseCase {

    override suspend fun invoke(
        amountInPlanks: BigInteger,
        originAsset: Asset,
        destinationChainId: ChainId,
        destinationAddress: String,
        originAddress: String,
        originFee: BigInteger?,
        confirmedValidations: List<TransferValidationResult>,
        transferMyselfAvailable: Boolean,
        skipEdValidation: Boolean,
        destinationFee: BigDecimal?
    ): Result<TransferValidationResult> = kotlin.runCatching {

        originFee ?: return Result.success(TransferValidationResult.WaitForFee)
        val originChainId = originAsset.token.configuration.chainId
        val originChain = chainsRepository.getChain(originChainId)
        val originAssetConfig = originAsset.token.configuration
        val originTransferable = originAsset.transferableInPlanks
        val originAvailable = originAsset.sendAvailableInPlanks
        val originExistentialDeposit = existentialDepositUseCase(originAssetConfig)
        val originEdFormatted = originExistentialDeposit.formatCryptoDetailFromPlanks(originAssetConfig)

        val amountDecimal = originAssetConfig.amountFromPlanks(amountInPlanks).orZero()


        val tip = if (originAssetConfig.isUtility && originChain.isEthereumChain.not()) walletConstants.tip(originChainId).orZero() else BigInteger.ZERO

        val isCrossChainTransfer = originChainId != destinationChainId

        val destinationChain = if (isCrossChainTransfer) chainsRepository.getChain(destinationChainId) else originChain
        val destinationAssetConfig = if (isCrossChainTransfer) destinationChain.assets.firstOrNull { it.symbol.removedXcPrefix() == originAssetConfig.symbol.removedXcPrefix() } else originAssetConfig

        val validateAddressResult = kotlin.runCatching { destinationChain.isValidAddress(destinationAddress) }

        val initialChecks = mapOf(
            TransferValidationResult.InvalidAddress to (validateAddressResult.getOrNull() in listOf(null, false)),
            TransferValidationResult.TransferToTheSameAddress to (!transferMyselfAvailable && destinationAddress == originAddress)
        )

        val initialCheck = performChecks(initialChecks, confirmedValidations, skipEdValidation)
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val destinationAccountId = destinationChain.accountIdOf(destinationAddress)

        val totalDestinationBalanceInPlanks = kotlin.runCatching {
            destinationAssetConfig?.let { walletRepository.getTotalBalance(it, destinationChain, destinationAccountId) }
        }.getOrNull().orZero()
        val totalDestinationBalanceDecimal = destinationAssetConfig?.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()

        val destinationExistentialDeposit = if (isCrossChainTransfer) existentialDepositUseCase(destinationAssetConfig ?: originAssetConfig) else originExistentialDeposit
        val destinationExistentialDepositDecimal = destinationAssetConfig?.amountFromPlanks(destinationExistentialDeposit).orZero()
        val destinationEdFormatted = destinationExistentialDepositDecimal.formatCryptoDetail(destinationAssetConfig?.symbol)
        val destinationResultFormatted = (totalDestinationBalanceDecimal + amountDecimal).formatCryptoDetail(destinationAssetConfig?.symbol)
        val destinationExtra = destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + amountDecimal)
        val destinationExtraFormatted = destinationExtra.formatCryptoDetail(destinationAssetConfig?.symbol)

        val metaAccount = accountRepository.getSelectedMetaAccount()

        val validationChecks = when {
            originAssetConfig.type == ChainAssetType.Equilibrium -> {
                getEquilibriumValidationChecks(originAsset, destinationAccountId, originChain, originAddress, amountInPlanks, originFee, tip, destinationExistentialDepositDecimal, destinationAssetConfig?.symbol, isCrossChainTransfer)
            }

            originChain.isSoraBasedChain() && destinationChain.isSoraBasedChain().not() -> {
                val utilityAsset = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let { utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it) }.orEmpty()

                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(originChain, destinationChain, originAsset, amountInPlanks)

                mapOf(
                    bridgeMinimumAmountValidation,
                    TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(originChain.name) to
                            if (destinationFee == null) {
                                false
                            } else {
                                amountDecimal < destinationFee
                            },
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > originAvailable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    getTransferValidationResultUtilityExistentialDeposit(isCrossChainTransfer, utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            originChain.isSoraBasedChain().not() && destinationChain.isSoraBasedChain() -> {
                val utilityAsset = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let { utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it) }.orEmpty()

                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(originChain, destinationChain, originAsset, amountInPlanks)

                mapOf(
                    bridgeMinimumAmountValidation,
                    TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(destinationChain.name) to
                            if (destinationFee == null) {
                                false
                            } else {
                                amountDecimal < destinationFee
                            },
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + originFee + tip > originAvailable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    getTransferValidationResultUtilityExistentialDeposit(isCrossChainTransfer, utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            originChain.isEthereumChain -> {
                val utilityAsset = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val destinationChainUtilityAsset = destinationChain.utilityAsset
                val totalDestinationUtilityAssetBalanceInPlanks = kotlin.runCatching { destinationChainUtilityAsset?.let { walletRepository.getTotalBalance(it, destinationChain, destinationAccountId) } }.getOrNull().orZero()
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull() ?: originTransferable) - (amountInPlanks + originFee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + originFee + tip > originAvailable),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    TransferValidationResult.DeadRecipientEthereum to (!originAsset.token.configuration.isUtility && totalDestinationUtilityAssetBalanceInPlanks.isZero())
                )
            }

            originAssetConfig.isUtility -> {
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull() ?: originTransferable) - (amountInPlanks + originFee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + originFee + tip > originAvailable),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            originAssetConfig.currencyId == bokoloCashTokenId -> { // xorlessTransfer
                val utilityAsset: Asset? = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }

                val feeRequiredTokenInPlanks = if (utilityAsset?.transferableInPlanks.orZero() < originFee) {
                    val swapDetails = utilityAsset?.let {
                        polkaswapInteractor.calcDetails(
                            availableDexPaths = listOf(0),
                            fromAsset = originAsset.token.configuration,
                            toAsset = utilityAsset.token.configuration,
                            amount = originAssetConfig.amountFromPlanks(originFee),
                            desired = WithDesired.OUTPUT,
                            slippageTolerance = 1.5,
                            market = Market.SMART
                        )
                    }?.onFailure {
                        if (it is InsufficientLiquidityException) {
                            return@runCatching TransferValidationResult.InsufficientBalance
                        }
                    }
                    swapDetails?.getOrNull()?.amount?.let { utilityAsset.token.planksFromAmount(it * FEE_RESERVE_TOLERANCE) }
                } else {
                    null
                }

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + feeRequiredTokenInPlanks.orZero() > originAvailable),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (originTransferable - amountInPlanks - feeRequiredTokenInPlanks.orZero() < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            else -> {
                val utilityAsset = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let { utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it) }.orEmpty()

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > originAvailable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    getTransferValidationResultUtilityExistentialDeposit(isCrossChainTransfer, utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations, skipEdValidation)
        return Result.success(result)
    }

    private fun getTransferValidationResultExistentialDeposit(isCrossChainTransfer: Boolean, assetEdFormatted: String) = if (isCrossChainTransfer) {
        TransferValidationResult.ExistentialDepositError(assetEdFormatted)
    } else {
        TransferValidationResult.ExistentialDepositWarning(assetEdFormatted)
    }

    private fun getTransferValidationResultUtilityExistentialDeposit(isCrossChainTransfer: Boolean, utilityEdFormatted: String) = if (isCrossChainTransfer) {
        TransferValidationResult.UtilityExistentialDepositError(utilityEdFormatted)
    } else {
        TransferValidationResult.UtilityExistentialDepositWarning(utilityEdFormatted)
    }

    private fun bridgeMinimumAmountValidation(
        originChain: Chain,
        destinationChain: Chain,
        asset: Asset,
        amountInPlanks: BigInteger
    ): Pair<TransferValidationResult.SubstrateBridgeMinimumAmountRequired, Boolean> {
        val minAmountTokens = when (originChain.id to destinationChain.id) {
            polkadotChainId to soraMainChainId,
            soraMainChainId to polkadotChainId -> "1.1"
            kusamaChainId to soraMainChainId -> "0.05"
            else -> null
        }
        val substrateBridgeMinAmountText = minAmountTokens?.let {
            "$minAmountTokens ${asset.token.configuration.symbol.uppercase()}"
        }.orEmpty()

        val substrateBridgeTransferMinAmount = minAmountTokens?.let {
            asset.token.planksFromAmount(BigDecimal(it))
        }

        return (TransferValidationResult.SubstrateBridgeMinimumAmountRequired(substrateBridgeMinAmountText)
                to (substrateBridgeTransferMinAmount != null && amountInPlanks < substrateBridgeTransferMinAmount))
    }

    override suspend fun validateExistentialDeposit(
        amountInPlanks: BigInteger,
        originAsset: Asset,
        destinationChainId: ChainId,
        destinationAddress: String,
        originAddress: String,
        originFee: BigInteger,
        confirmedValidations: List<TransferValidationResult>
    ): Result<TransferValidationResult> = kotlin.runCatching {
        val originChainId = originAsset.token.configuration.chainId
        val originChain = chainsRepository.getChain(originChainId)
        val originAssetConfig = originAsset.token.configuration
        val transferable = originAsset.transferableInPlanks
        val originExistentialDeposit = existentialDepositUseCase(originAssetConfig)

        val tip = if (originAssetConfig.isUtility) walletConstants.tip(originChainId).orZero() else BigInteger.ZERO
        val amountDecimal = originAssetConfig.amountFromPlanks(amountInPlanks).orZero()

        val destinationChain = chainsRepository.getChain(destinationChainId)
        val destinationAsset = destinationChain.assets.firstOrNull { it.symbol == originAsset.token.configuration.symbol }
        val destinationExistentialDeposit = existentialDepositUseCase(destinationAsset ?: originAssetConfig)
        val destinationExistentialDepositDecimal = destinationAsset?.amountFromPlanks(destinationExistentialDeposit).orZero()

        val destinationAccountId = destinationChain.accountIdOf(destinationAddress)
        val totalDestinationBalanceInPlanks = destinationAsset?.let { walletRepository.getTotalBalance(it, destinationChain, destinationAccountId) }.orZero()
        val totalDestinationBalanceDecimal = destinationAsset?.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()
        val isCrossChainTransfer = originChainId != destinationChainId

        val destinationEdFormatted = destinationExistentialDepositDecimal.formatCryptoDetail(destinationAsset?.symbol)
        val destinationResultFormatted = (totalDestinationBalanceDecimal + amountDecimal).formatCryptoDetail(destinationAsset?.symbol)
        val destinationExtra = destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + amountDecimal)
        val destinationExtraFormatted = destinationExtra.formatCryptoDetail(destinationAsset?.symbol)

        val validationChecks = when {
            originAssetConfig.type == ChainAssetType.Equilibrium -> {
                getEquilibriumValidationChecks(originAsset, destinationAccountId, originChain, originAddress, amountInPlanks, originFee, tip, destinationExistentialDepositDecimal, destinationAsset?.symbol, isCrossChainTransfer)
            }

            originAssetConfig.isUtility -> {
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull() ?: transferable) - (amountInPlanks + originFee + tip)
                val assetEdFormatted = originExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
                mapOf(
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, assetEdFormatted) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            else -> {
                val originEdFormatted = originExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
                mapOf(
                    getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originEdFormatted) to (transferable - amountInPlanks < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations)
        return Result.success(result)
    }

    private suspend fun getEquilibriumValidationChecks(
        originAsset: Asset,
        destinationAccountId: ByteArray,
        originChain: Chain,
        originAddress: String,
        amountInPlanks: BigInteger,
        originFee: BigInteger,
        tip: BigInteger,
        destinationExistentialDepositDecimal: BigDecimal,
        destinationAssetSymbol: String?,
        isCrossChainTransfer: Boolean
    ): Map<TransferValidationResult, Boolean> {
        val originAssetConfig = originAsset.token.configuration
        val originAssetExistentialDeposit = existentialDepositUseCase(originAssetConfig)

        val destinationAccountInfo = walletRepository.getEquilibriumAccountInfo(originAssetConfig, destinationAccountId)
        val originAccountInfo = walletRepository.getEquilibriumAccountInfo(originAssetConfig, originChain.accountIdOf(originAddress))
        val assetRates = walletRepository.getEquilibriumAssetRates(originAssetConfig)

        val originNewTotal = originAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
            val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

            val newAmount = when {
                originAssetConfig.isUtility -> {
                    if (eqAssetId == originAssetConfig.currency) {
                        if (amount < amountInPlanks + originFee + tip) return mapOf(TransferValidationResult.InsufficientBalance to true)
                        amount - amountInPlanks - originFee - tip
                    } else {
                        amount
                    }
                }

                eqAssetId == originAssetConfig.currency -> {
                    if (amount < amountInPlanks) return mapOf(TransferValidationResult.InsufficientBalance to true)
                    amount - amountInPlanks
                }

                eqAssetId == originChain.utilityAsset?.currency -> {
                    if (amount < originFee + tip) return mapOf(TransferValidationResult.InsufficientUtilityAssetBalance to true)
                    amount - originFee - tip
                }

                else -> amount
            }

            val amountDecimal = originAsset.token.configuration.amountFromPlanks(newAmount)
            val rateDecimal = originAsset.token.configuration.amountFromPlanks(tokenRateInPlanks)

            amountDecimal * rateDecimal
        }.orZero()

        val destinationNewTotal = destinationAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
            val newAmount = if (eqAssetId == originAssetConfig.currency) amount + amountInPlanks else amount
            val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

            val amountDecimal = originAsset.token.configuration.amountFromPlanks(newAmount)
            val rateDecimal = originAsset.token.configuration.amountFromPlanks(tokenRateInPlanks)

            amountDecimal * rateDecimal
        }.orZero()

        val originNewTotalInPlanks = originAsset.token.configuration.planksFromAmount(originNewTotal)

        val originAssetEdFormatted = originAssetExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
        val destinationEdFormatted = destinationExistentialDepositDecimal.formatCryptoDetail(destinationAssetSymbol)
        val destinationResultFormatted = destinationNewTotal.formatCryptoDetail(destinationAssetSymbol)
        val destinationExtra = destinationExistentialDepositDecimal - destinationNewTotal
        val destinationExtraFormatted = destinationExtra.formatCryptoDetail(destinationAssetSymbol)

        return mapOf(
            getTransferValidationResultExistentialDeposit(isCrossChainTransfer, originAssetEdFormatted) to (originNewTotalInPlanks < originAssetExistentialDeposit),
            TransferValidationResult.DeadRecipient(destinationResultFormatted, destinationEdFormatted, destinationExtraFormatted) to (destinationNewTotal < destinationExistentialDepositDecimal)
        )
    }

    private fun performChecks(
        checks: Map<TransferValidationResult, Boolean>,
        confirmedValidations: List<TransferValidationResult>,
        skipEdValidation: Boolean = false
    ): TransferValidationResult {
        checks.filterNot { (result, _) ->
            result in confirmedValidations || skipEdValidation && result.isExistentialDepositWarning
        }.forEach { (result, condition) ->
            if (condition) return result
        }
        return TransferValidationResult.Valid
    }
}
