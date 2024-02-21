package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.isSoraBasedChain
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.kusamaChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.FEE_RESERVE_TOLERANCE

class ValidateTransferUseCaseImpl(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val polkaswapInteractor: PolkaswapInteractor
) : ValidateTransferUseCase {

    override suspend fun invoke(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationChainId: ChainId,
        recipientAddress: String,
        ownAddress: String,
        fee: BigInteger?,
        confirmedValidations: List<TransferValidationResult>,
        transferMyselfAvailable: Boolean,
        skipEdValidation: Boolean,
        destinationFeeAmount: BigDecimal?
    ): Result<TransferValidationResult> = kotlin.runCatching {
        fee ?: return Result.success(TransferValidationResult.WaitForFee)
        val chainId = asset.token.configuration.chainId
        val originChain = chainRegistry.getChain(chainId)
        val destinationChain = chainRegistry.getChain(destinationChainId)
        val chainAsset = asset.token.configuration
        val destinationAsset = destinationChain.assets.firstOrNull { it.symbol.removedXcPrefix() == asset.token.configuration.symbol.removedXcPrefix() }
        val transferable = asset.transferableInPlanks
        val assetExistentialDeposit = existentialDepositUseCase(chainAsset)
        val assetEdFormatted = assetExistentialDeposit.formatCryptoDetailFromPlanks(chainAsset)
        val tip = if (chainAsset.isUtility && originChain.isEthereumChain.not()) walletConstants.tip(chainId).orZero() else BigInteger.ZERO

        val validateAddressResult = kotlin.runCatching { destinationChain.isValidAddress(recipientAddress) }

        val initialChecks = mapOf(
            TransferValidationResult.InvalidAddress to (validateAddressResult.getOrNull() in listOf(null, false)),
            TransferValidationResult.TransferToTheSameAddress to (!transferMyselfAvailable && recipientAddress == ownAddress)
        )

        val initialCheck = performChecks(initialChecks, confirmedValidations, skipEdValidation)
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val recipientAccountId = destinationChain.accountIdOf(recipientAddress)

        val totalRecipientBalanceInPlanks =
            kotlin.runCatching { destinationAsset?.let { walletRepository.getTotalBalance(it, destinationChain, recipientAccountId) } }.getOrNull().orZero()

        val destinationExistentialDeposit = existentialDepositUseCase(destinationAsset ?: chainAsset)

        val validationChecks = when {
            chainAsset.type == ChainAssetType.Equilibrium -> {
                getEquilibriumValidationChecks(asset, recipientAccountId, originChain, ownAddress, amountInPlanks, fee, tip, destinationExistentialDeposit)
            }

            originChain.isEthereumChain -> {
                val metaAccount = accountRepository.getSelectedMetaAccount()
                val utilityAsset = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val originChainUtilityAsset = originChain.utilityAsset
                val totalRecipientUtilityAssetBalanceInPlanks = kotlin.runCatching { originChainUtilityAsset?.let { walletRepository.getTotalBalance(it, destinationChain, recipientAccountId) } }.getOrNull().orZero()
                val resultedBalance = (asset.freeInPlanks ?: transferable) - (amountInPlanks + fee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + fee + tip > transferable),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (resultedBalance < assetExistentialDeposit),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    TransferValidationResult.DeadRecipientEthereum to (!asset.token.configuration.isUtility && totalRecipientUtilityAssetBalanceInPlanks.isZero())
                )
            }

            destinationChain.isSoraBasedChain().not() && originChain.isSoraBasedChain() -> {
                val metaAccount = accountRepository.getSelectedMetaAccount()
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

                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(destinationChain, asset, amountInPlanks)

                mapOf(
                    bridgeMinimumAmountValidation,
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > transferable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (transferable - amountInPlanks < assetExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositWarning(utilityEdFormatted) to (utilityAssetBalance - (fee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < assetExistentialDeposit)
                )
            }

            destinationChain.isSoraBasedChain() && originChain.isSoraBasedChain().not() -> {
                val metaAccount = accountRepository.getSelectedMetaAccount()
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

                val destinationFeeAmountInPlanks = destinationFeeAmount?.let { destinationAsset?.planksFromAmount(it) }
                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(originChain, asset, amountInPlanks)

                mapOf(
                    bridgeMinimumAmountValidation,
                    TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(originChain.name) to
                            if (destinationFeeAmountInPlanks == null) {
                                false
                            } else {
                                amountInPlanks < destinationFeeAmountInPlanks
                            },
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + fee + tip > transferable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (transferable - amountInPlanks < assetExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositWarning(utilityEdFormatted) to (utilityAssetBalance - (fee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < assetExistentialDeposit)
                )
            }

            chainAsset.isUtility -> {
                val resultedBalance = (asset.freeInPlanks ?: transferable) - (amountInPlanks + fee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + fee + tip > transferable),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (resultedBalance < assetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < destinationExistentialDeposit)
                )
            }

            chainAsset.currencyId == bokoloCashTokenId -> { // xorlessTransfer
                val metaAccount = accountRepository.getSelectedMetaAccount()
                val utilityAsset: Asset? = originChain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(originChain)!!,
                        it,
                        originChain.minSupportedVersion
                    )
                }

                val feeRequiredTokenInPlanks = if (utilityAsset?.transferableInPlanks.orZero() < fee) {
                    val swapDetails = utilityAsset?.let {
                        polkaswapInteractor.calcDetails(
                            availableDexPaths = listOf(0),
                            tokenFrom = asset,
                            tokenTo = utilityAsset,
                            amount = asset.token.amountFromPlanks(fee),
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
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + feeRequiredTokenInPlanks.orZero() > transferable),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (transferable - amountInPlanks - feeRequiredTokenInPlanks.orZero() < assetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < destinationExistentialDeposit)
                )
            }

            else -> {
                val metaAccount = accountRepository.getSelectedMetaAccount()
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
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > transferable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositWarning(assetEdFormatted) to (transferable - amountInPlanks < assetExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositWarning(utilityEdFormatted) to (utilityAssetBalance - (fee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < destinationExistentialDeposit)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations, skipEdValidation)
        return Result.success(result)
    }

    private fun bridgeMinimumAmountValidation(
        utilityChain: Chain,
        asset: Asset,
        amountInPlanks: BigInteger
    ): Pair<TransferValidationResult.SubstrateBridgeMinimumAmountRequired, Boolean> {
        val minAmountTokens = when (utilityChain.utilityAsset?.chainId) {
            polkadotChainId -> "1.1"
            kusamaChainId -> "0.05"
            else -> null
        }
        val substrateBridgeIncomingTransferMinAmountText = minAmountTokens?.let {
            "$minAmountTokens ${asset.token.configuration.symbol.uppercase()}"
        }.orEmpty()

        val substrateBridgeTransferMinAmount = minAmountTokens?.let {
            asset.token.planksFromAmount(BigDecimal(it))
        }

        return (TransferValidationResult.SubstrateBridgeMinimumAmountRequired(substrateBridgeIncomingTransferMinAmountText)
                to (substrateBridgeTransferMinAmount != null && amountInPlanks < substrateBridgeTransferMinAmount))
    }

    override suspend fun validateExistentialDeposit(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationChainId: ChainId,
        recipientAddress: String,
        ownAddress: String,
        fee: BigInteger,
        confirmedValidations: List<TransferValidationResult>
    ): Result<TransferValidationResult> = kotlin.runCatching {
        val chainId = asset.token.configuration.chainId
        val originChain = chainRegistry.getChain(chainId)
        val chainAsset = asset.token.configuration
        val destinationChain = chainRegistry.getChain(destinationChainId)
        val destinationAsset = destinationChain.assets.firstOrNull { it.symbol == asset.token.configuration.symbol }
        val transferable = asset.transferableInPlanks
        val assetExistentialDeposit = existentialDepositUseCase(chainAsset)
        val destinationExistentialDeposit = existentialDepositUseCase(destinationAsset ?: chainAsset)
        val tip = if (chainAsset.isUtility) walletConstants.tip(chainId).orZero() else BigInteger.ZERO

        val recipientAccountId = destinationChain.accountIdOf(recipientAddress)
        val totalRecipientBalanceInPlanks = destinationAsset?.let { walletRepository.getTotalBalance(it, destinationChain, recipientAccountId) }.orZero()

        val validationChecks = when {
            chainAsset.type == ChainAssetType.Equilibrium -> {
                getEquilibriumValidationChecks(asset, recipientAccountId, originChain, ownAddress, amountInPlanks, fee, tip, destinationExistentialDeposit)
            }

            chainAsset.isUtility -> {
                val resultedBalance = (asset.freeInPlanks ?: transferable) - (amountInPlanks + fee + tip)
                mapOf(
                    TransferValidationResult.ExistentialDepositWarning(assetExistentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration)) to (resultedBalance < assetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < destinationExistentialDeposit)
                )
            }

            else -> {
                mapOf(
                    TransferValidationResult.ExistentialDepositWarning(assetExistentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration)) to (transferable - amountInPlanks < assetExistentialDeposit),
                    TransferValidationResult.DeadRecipient to (totalRecipientBalanceInPlanks + amountInPlanks < destinationExistentialDeposit)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations)
        return Result.success(result)
    }

    private suspend fun getEquilibriumValidationChecks(
        asset: Asset,
        recipientAccountId: ByteArray,
        chain: Chain,
        ownAddress: String,
        amountInPlanks: BigInteger,
        fee: BigInteger,
        tip: BigInteger,
        destinationExistentialDeposit: BigInteger
    ): Map<TransferValidationResult, Boolean> {
        val chainAsset = asset.token.configuration
        val assetExistentialDeposit = existentialDepositUseCase(chainAsset)

        val recipientAccountInfo = walletRepository.getEquilibriumAccountInfo(chainAsset, recipientAccountId)
        val ownAccountInfo = walletRepository.getEquilibriumAccountInfo(chainAsset, chain.accountIdOf(ownAddress))
        val assetRates = walletRepository.getEquilibriumAssetRates(chainAsset)

        val ownNewTotal = ownAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
            val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

            val newAmount = when {
                chainAsset.isUtility -> {
                    if (eqAssetId == chainAsset.currency) {
                        if (amount < amountInPlanks + fee + tip) return mapOf(TransferValidationResult.InsufficientBalance to true)
                        amount - amountInPlanks - fee - tip
                    } else {
                        amount
                    }
                }

                eqAssetId == chainAsset.currency -> {
                    if (amount < amountInPlanks) return mapOf(TransferValidationResult.InsufficientBalance to true)
                    amount - amountInPlanks
                }

                eqAssetId == chain.utilityAsset?.currency -> {
                    if (amount < fee + tip) return mapOf(TransferValidationResult.InsufficientUtilityAssetBalance to true)
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

        return mapOf(
            TransferValidationResult.ExistentialDepositWarning(assetExistentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration)) to (ownNewTotalInPlanks < assetExistentialDeposit),
            TransferValidationResult.DeadRecipient to (recipientNewTotalInPlanks < destinationExistentialDeposit)
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
