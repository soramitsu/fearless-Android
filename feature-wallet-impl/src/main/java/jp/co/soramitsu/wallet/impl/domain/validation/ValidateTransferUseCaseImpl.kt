package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.isSoraBasedChain
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.core.utils.removedXcPrefix
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
import java.math.BigDecimal
import java.math.BigInteger

class ValidateTransferUseCaseImpl(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val chainsRepository: ChainsRepository,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val polkaswapInteractor: PolkaswapInteractor,
) : ValidateTransferUseCase {

    override suspend fun validateTransfer(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationAddress: String,
        senderAddress: String,
        fee: BigInteger?,
        confirmedValidations: List<TransferValidationResult>,
        transferMyselfAvailable: Boolean,
        skipEdValidation: Boolean
    ): Result<TransferValidationResult> = kotlin.runCatching {

        fee ?: return Result.success(TransferValidationResult.WaitForFee)

        val chainId = asset.token.configuration.chainId
        val chain = chainsRepository.getChain(chainId)

        val validateAddressResult = kotlin.runCatching { chain.isValidAddress(destinationAddress) }

        val initialChecks = mapOf(
            TransferValidationResult.InvalidAddress to (validateAddressResult.getOrNull() in listOf(
                null,
                false
            )),
            TransferValidationResult.TransferToTheSameAddress to (!transferMyselfAvailable && destinationAddress == senderAddress)
        )

        val initialCheck = performChecks(initialChecks, confirmedValidations, skipEdValidation)
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val validationChecksProvider = when (chain.ecosystem) {
            Ecosystem.EthereumBased,
            Ecosystem.Substrate -> SubstrateValidationChecksProvider(
                accountRepository,
                walletRepository,
                chainsRepository,
                existentialDepositUseCase,
                walletConstants,
                polkaswapInteractor
            )
            Ecosystem.Ethereum -> EthereumValidationChecksProvider(
                chainsRepository,
                accountRepository,
                existentialDepositUseCase,
                walletRepository
            )
            Ecosystem.Ton -> TonValidationChecksProvider(chainsRepository,accountRepository, walletRepository)
        }

        val validationChecks = validationChecksProvider.provide(amountInPlanks, asset, destinationAddress, fee)
        val result = performChecks(validationChecks, confirmedValidations, skipEdValidation)
        return Result.success(result)
    }

    override suspend fun validateXcmTransfer(
        amountInPlanks: BigInteger,
        originAsset: Asset,
        destinationChainId: ChainId,
        destinationAddress: String,
        originAddress: String,
        originFee: BigInteger?,
        confirmedValidations: List<TransferValidationResult>,
        transferMyselfAvailable: Boolean,
        destinationFee: BigDecimal?
    ): Result<TransferValidationResult> = kotlin.runCatching {
        originFee ?: return Result.success(TransferValidationResult.WaitForFee)
        val originChainId = originAsset.token.configuration.chainId
        val originChain = chainsRepository.getChain(originChainId)
        val originAssetConfig = originAsset.token.configuration
        val originTransferable = originAsset.transferableInPlanks
        val originAvailable = originAsset.sendAvailableInPlanks
        val originExistentialDeposit = existentialDepositUseCase(originAssetConfig)
        val originEdFormatted =
            originExistentialDeposit.formatCryptoDetailFromPlanks(originAssetConfig)

        val amountDecimal = originAssetConfig.amountFromPlanks(amountInPlanks).orZero()

        val tip =
            if (originAssetConfig.isUtility && originChain.isEthereumChain.not()) walletConstants.tip(
                originChainId
            ).orZero() else BigInteger.ZERO

        val destinationChain = chainsRepository.getChain(destinationChainId)
        val destinationAssetConfig =
            destinationChain.assets.firstOrNull { it.symbol.removedXcPrefix() == originAssetConfig.symbol.removedXcPrefix() }

        val validateAddressResult =
            kotlin.runCatching { destinationChain.isValidAddress(destinationAddress) }

        val initialChecks = mapOf(
            TransferValidationResult.InvalidAddress to (validateAddressResult.getOrNull() in listOf(
                null,
                false
            )),
            TransferValidationResult.TransferToTheSameAddress to (!transferMyselfAvailable && destinationAddress == originAddress)
        )

        val initialCheck = performChecks(initialChecks, confirmedValidations, false)
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        val totalDestinationBalanceInPlanks = kotlin.runCatching {
            destinationAssetConfig?.let {
                walletRepository.getTotalBalance(
                    it,
                    destinationChain,
                    destinationAddress
                )
            }
        }.getOrNull().orZero()
        val totalDestinationBalanceDecimal =
            destinationAssetConfig?.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()

        val destinationExistentialDeposit =
            existentialDepositUseCase(destinationAssetConfig ?: originAssetConfig)
        val destinationExistentialDepositDecimal =
            destinationAssetConfig?.amountFromPlanks(destinationExistentialDeposit).orZero()
        val destinationEdFormatted =
            destinationExistentialDepositDecimal.formatCryptoDetail(destinationAssetConfig?.symbol)
        val destinationResultFormatted =
            (totalDestinationBalanceDecimal + amountDecimal).formatCryptoDetail(
                destinationAssetConfig?.symbol
            )
        val destinationExtra =
            destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + amountDecimal)
        val destinationExtraFormatted =
            destinationExtra.formatCryptoDetail(destinationAssetConfig?.symbol)

        val metaAccount = accountRepository.getSelectedMetaAccount()

        val validationChecks = when {
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
                val utilityAssetExistentialDeposit =
                    originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let {
                    utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it)
                }.orEmpty()

                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(
                    originChain,
                    destinationChain,
                    originAsset,
                    amountInPlanks
                )

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
                    TransferValidationResult.ExistentialDepositError(originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositError(utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
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
                val utilityAssetExistentialDeposit =
                    originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let {
                    utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it)
                }.orEmpty()

                val bridgeMinimumAmountValidation = bridgeMinimumAmountValidation(
                    originChain,
                    destinationChain,
                    originAsset,
                    amountInPlanks
                )

                mapOf(
                    bridgeMinimumAmountValidation,
                    TransferValidationResult.SubstrateBridgeAmountLessThenFeeWarning(
                        destinationChain.name
                    ) to
                            if (destinationFee == null) {
                                false
                            } else {
                                amountDecimal < destinationFee
                            },
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + originFee + tip > originAvailable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositError(originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositError(utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            originAssetConfig.isUtility -> {
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull()
                    ?: originTransferable) - (amountInPlanks + originFee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + originFee + tip > originAvailable),
                    TransferValidationResult.ExistentialDepositError(originEdFormatted) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
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
                val utilityAssetExistentialDeposit =
                    originChain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let {
                    utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it)
                }.orEmpty()

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > originAvailable),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (originFee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositError(originEdFormatted) to (originTransferable - amountInPlanks < originExistentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositError(utilityEdFormatted) to (utilityAssetBalance - (originFee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations, false)
        return Result.success(result)
    }

    private fun getTransferValidationResultExistentialDeposit(
        isCrossChainTransfer: Boolean,
        assetEdFormatted: String
    ) = if (isCrossChainTransfer) {
        TransferValidationResult.ExistentialDepositError(assetEdFormatted)
    } else {
        TransferValidationResult.ExistentialDepositWarning(assetEdFormatted)
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

        return (TransferValidationResult.SubstrateBridgeMinimumAmountRequired(
            substrateBridgeMinAmountText
        )
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
        val originAssetConfig = originAsset.token.configuration
        val transferable = originAsset.transferableInPlanks
        val originExistentialDeposit = existentialDepositUseCase(originAssetConfig)

        val tip = if (originAssetConfig.isUtility) walletConstants.tip(originChainId)
            .orZero() else BigInteger.ZERO
        val amountDecimal = originAssetConfig.amountFromPlanks(amountInPlanks).orZero()

        val destinationChain = chainsRepository.getChain(destinationChainId)
        val destinationAsset =
            destinationChain.assets.firstOrNull { it.symbol == originAsset.token.configuration.symbol }
        val destinationExistentialDeposit =
            existentialDepositUseCase(destinationAsset ?: originAssetConfig)
        val destinationExistentialDepositDecimal =
            destinationAsset?.amountFromPlanks(destinationExistentialDeposit).orZero()

        val totalDestinationBalanceInPlanks = destinationAsset?.let {
            walletRepository.getTotalBalance(
                it,
                destinationChain,
                destinationAddress
            )
        }.orZero()
        val totalDestinationBalanceDecimal =
            destinationAsset?.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()
        val isCrossChainTransfer = originChainId != destinationChainId

        val destinationEdFormatted =
            destinationExistentialDepositDecimal.formatCryptoDetail(destinationAsset?.symbol)
        val destinationResultFormatted =
            (totalDestinationBalanceDecimal + amountDecimal).formatCryptoDetail(destinationAsset?.symbol)
        val destinationExtra =
            destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + amountDecimal)
        val destinationExtraFormatted =
            destinationExtra.formatCryptoDetail(destinationAsset?.symbol)

        val validationChecks = when {
            originAssetConfig.isUtility -> {
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull()
                    ?: transferable) - (amountInPlanks + originFee + tip)
                val assetEdFormatted =
                    originExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
                mapOf(
                    getTransferValidationResultExistentialDeposit(
                        isCrossChainTransfer,
                        assetEdFormatted
                    ) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }

            else -> {
                mapOf<TransferValidationResult, Boolean>(
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < destinationExistentialDepositDecimal)
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations)
        return Result.success(result)
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
