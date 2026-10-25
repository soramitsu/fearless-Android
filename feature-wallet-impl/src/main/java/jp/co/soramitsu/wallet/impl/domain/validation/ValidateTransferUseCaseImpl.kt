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
import jp.co.soramitsu.core.utils.isEvmChecksumValid
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.core.utils.normalizedAssetSymbol
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.addressByteOrNull
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
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

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

        if (asset.transferableInPlanks == null || asset.sendAvailableInPlanks == null) {
            return Result.success(TransferValidationResult.BalanceUnavailable)
        }
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
    }.onFailure { if (it is CancellationException) throw it }

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
        val originAssetConfig = originAsset.token.configuration
        val originTransferable = originAsset.transferableInPlanks
            ?: return Result.success(TransferValidationResult.BalanceUnavailable)
        val originAvailable = originAsset.sendAvailableInPlanks
            ?: return Result.success(TransferValidationResult.BalanceUnavailable)
        val originChain = chainsRepository.getChain(originChainId)
        // Cross-chain minimums are errors, not warnings: an unknown one stops the validation.
        val originExistentialDeposit = existentialDepositUseCase(originAssetConfig)
            .onFailure { if (it is CancellationException) throw it }
            .getOrElse { return Result.success(existentialDepositUnavailable(originAssetConfig)) }
        val originEdFormatted =
            originExistentialDeposit.formatCryptoDetailFromPlanks(originAssetConfig)

        val amountDecimal = originAssetConfig.amountFromPlanks(amountInPlanks).orZero()

        val tip =
            if (originAssetConfig.isUtility && originChain.isEthereumChain.not()) walletConstants.tip(
                originChainId
            ).orZero() else BigInteger.ZERO

        val destinationChain = chainsRepository.getChain(destinationChainId)
        val destinationAssetConfig = crossChainDestinationAsset(originAssetConfig, destinationChain)

        // Submission takes the address as entered: surrounding whitespace, a broken EIP-55 checksum and a Substrate
        // address in another network's format, which the shared address check tolerates, are refused here instead
        // of after the user confirmed.
        val hasDestinationFormat = destinationChain.ecosystem != Ecosystem.Substrate ||
            destinationAddress.addressByteOrNull() == destinationChain.addressPrefix.toShort()
        val isAddressValid = destinationAddress == destinationAddress.trim() &&
            kotlin.runCatching { destinationChain.isValidAddress(destinationAddress) }.getOrNull() == true &&
            (destinationChain.ecosystem !in EVM_ECOSYSTEMS || destinationAddress.isEvmChecksumValid()) &&
            hasDestinationFormat

        val initialChecks = mapOf(
            TransferValidationResult.InvalidAddress to !isAddressValid,
            TransferValidationResult.TransferToTheSameAddress to (!transferMyselfAvailable && destinationAddress == originAddress)
        )

        val initialCheck = performChecks(initialChecks, confirmedValidations, false)
        if (initialCheck != TransferValidationResult.Valid) {
            return Result.success(initialCheck)
        }

        // An unknown destination asset, recipient balance or minimum stops the transfer: taken as zero, they let an
        // amount through that arrives below the destination minimum and is lost there.
        destinationAssetConfig ?: return Result.success(TransferValidationResult.CrossChainDestinationUnavailable)
        val totalDestinationBalanceInPlanks = destinationBalance(destinationAssetConfig, destinationChain, destinationAddress)
            ?: return Result.success(TransferValidationResult.CrossChainDestinationUnavailable)
        val totalDestinationBalanceDecimal = destinationAssetConfig.amountFromPlanks(totalDestinationBalanceInPlanks)

        val destinationExistentialDeposit = existentialDepositUseCase(destinationAssetConfig)
            .onFailure { if (it is CancellationException) throw it }
            .getOrElse { return Result.success(existentialDepositUnavailable(destinationAssetConfig)) }
        val destinationExistentialDepositDecimal = destinationAssetConfig.amountFromPlanks(destinationExistentialDeposit)
        // A fixed destination fee is paid on top of the amount, so the whole amount arrives. A fee-included route
        // deducts an unquoted fee at the destination, which its reviewed minimum amount has to cover.
        val arrivingDecimal = amountDecimal
        val destinationEdFormatted =
            destinationExistentialDepositDecimal.formatCryptoDetail(destinationAssetConfig.symbol)
        val destinationResultFormatted =
            (totalDestinationBalanceDecimal + arrivingDecimal).formatCryptoDetail(destinationAssetConfig.symbol)
        val destinationExtra =
            destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + arrivingDecimal)
        val destinationExtraFormatted =
            destinationExtra.formatCryptoDetail(destinationAssetConfig.symbol)
        val recipientWouldBeRemoved = totalDestinationBalanceDecimal + arrivingDecimal < destinationExistentialDepositDecimal

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
                val utilityAssetBalance = utilityAsset?.transferableInPlanks
                    ?: return Result.success(TransferValidationResult.BalanceUnavailable)
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { utility ->
                    existentialDepositUseCase(utility).onFailure { if (it is CancellationException) throw it }
                        .getOrElse { return Result.success(existentialDepositUnavailable(utility)) }
                }.orZero()
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
                    ) to recipientWouldBeRemoved
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
                val utilityAssetBalance = utilityAsset?.transferableInPlanks
                    ?: return Result.success(TransferValidationResult.BalanceUnavailable)
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { utility ->
                    existentialDepositUseCase(utility).onFailure { if (it is CancellationException) throw it }
                        .getOrElse { return Result.success(existentialDepositUnavailable(utility)) }
                }.orZero()
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
                    ) to recipientWouldBeRemoved
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
                    ) to recipientWouldBeRemoved
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
                val utilityAssetBalance = utilityAsset?.transferableInPlanks
                    ?: return Result.success(TransferValidationResult.BalanceUnavailable)
                val utilityAssetExistentialDeposit = originChain.utilityAsset?.let { utility ->
                    existentialDepositUseCase(utility).onFailure { if (it is CancellationException) throw it }
                        .getOrElse { return Result.success(existentialDepositUnavailable(utility)) }
                }.orZero()
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
                    ) to recipientWouldBeRemoved
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations, false)
        return Result.success(result)
    }.onFailure { if (it is CancellationException) throw it }

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
        confirmedValidations: List<TransferValidationResult>,
        destinationFeeInPlanks: BigInteger
    ): Result<TransferValidationResult> = kotlin.runCatching {
        val originChainId = originAsset.token.configuration.chainId
        val originAssetConfig = originAsset.token.configuration
        val isCrossChainTransfer = originChainId != destinationChainId
        require(destinationFeeInPlanks.signum() >= 0 && destinationFeeInPlanks <= amountInPlanks) {
            "The destination fee must be part of the transferred amount"
        }
        require(isCrossChainTransfer || destinationFeeInPlanks.signum() == 0) {
            "A transfer on one network has no destination fee"
        }
        val transferable = originAsset.transferableInPlanks
            ?: return Result.success(TransferValidationResult.BalanceUnavailable)
        val originExistentialDepositResult = existentialDepositUseCase(originAssetConfig)
            .onFailure { if (it is CancellationException) throw it }
        val originExistentialDeposit = originExistentialDepositResult.getOrNull().orZero()

        val tip = if (originAssetConfig.isUtility) walletConstants.tip(originChainId)
            .orZero() else BigInteger.ZERO

        val destinationChain = chainsRepository.getChain(destinationChainId)
        // On one network the recipient receives the asset that is sent, not another one with the same symbol.
        val destinationAsset = if (isCrossChainTransfer) {
            crossChainDestinationAsset(originAssetConfig, destinationChain)
                ?: return Result.success(TransferValidationResult.CrossChainDestinationUnavailable)
        } else {
            originAssetConfig
        }
        val destinationExistentialDepositResult = existentialDepositUseCase(destinationAsset)
            .onFailure { if (it is CancellationException) throw it }
        val destinationExistentialDeposit = destinationExistentialDepositResult.getOrNull().orZero()
        val destinationExistentialDepositDecimal = destinationAsset.amountFromPlanks(destinationExistentialDeposit)
        val existentialDepositUnknown = existentialDepositUnknownCheck(
            originAssetConfig to originExistentialDepositResult,
            destinationAsset to destinationExistentialDepositResult
        )

        val totalDestinationBalanceInPlanks = if (isCrossChainTransfer) {
            destinationBalance(destinationAsset, destinationChain, destinationAddress)
                ?: return Result.success(TransferValidationResult.CrossChainDestinationUnavailable)
        } else {
            walletRepository.getTotalBalance(destinationAsset, destinationChain, destinationAddress)
        }
        val totalDestinationBalanceDecimal = destinationAsset.amountFromPlanks(totalDestinationBalanceInPlanks)
        if (isCrossChainTransfer) {
            // Cross-chain minimums are errors, not warnings: an unknown one stops the validation.
            originExistentialDepositResult.onFailure { return Result.success(existentialDepositUnavailable(originAssetConfig)) }
            destinationExistentialDepositResult.onFailure { return Result.success(existentialDepositUnavailable(destinationAsset)) }
        }

        // The destination network keeps its execution fee out of the transferred amount; only the rest arrives.
        val arrivingDecimal = originAssetConfig.amountFromPlanks(amountInPlanks - destinationFeeInPlanks).orZero()
        val destinationEdFormatted =
            destinationExistentialDepositDecimal.formatCryptoDetail(destinationAsset.symbol)
        val destinationResultFormatted =
            (totalDestinationBalanceDecimal + arrivingDecimal).formatCryptoDetail(destinationAsset.symbol)
        val destinationExtra =
            destinationExistentialDepositDecimal - (totalDestinationBalanceDecimal + arrivingDecimal)
        val destinationExtraFormatted =
            destinationExtra.formatCryptoDetail(destinationAsset.symbol)
        val recipientWouldBeRemoved = totalDestinationBalanceDecimal + arrivingDecimal < destinationExistentialDepositDecimal

        val validationChecks = when {
            originAssetConfig.isUtility -> {
                val resultedBalance = (originAsset.freeInPlanks.positiveOrNull()
                    ?: transferable) - (amountInPlanks + originFee + tip)
                val assetEdFormatted =
                    originExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
                mapOf(
                    existentialDepositUnknown,
                    getTransferValidationResultExistentialDeposit(
                        isCrossChainTransfer,
                        assetEdFormatted
                    ) to (resultedBalance < originExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to recipientWouldBeRemoved
                )
            }

            else -> {
                mapOf(
                    existentialDepositUnknown,
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        destinationEdFormatted,
                        destinationExtraFormatted
                    ) to recipientWouldBeRemoved
                )
            }
        }
        val result = performChecks(validationChecks, confirmedValidations)
        return Result.success(result)
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * The destination copy of the transferred asset: the only asset of [destinationChain] with the same normalized
     * symbol. Setup and confirmation look it up the same way; none or several matches leave it unknown.
     */
    private fun crossChainDestinationAsset(originAsset: CoreAsset, destinationChain: Chain): CoreAsset? {
        val symbol = originAsset.symbol.normalizedAssetSymbol()
        return destinationChain.assets.singleOrNull { it.symbol.normalizedAssetSymbol() == symbol }
    }

    /** The recipient's balance on the destination network, or null when it cannot be read. */
    private suspend fun destinationBalance(asset: CoreAsset, chain: Chain, address: String): BigInteger? =
        try {
            walletRepository.getTotalBalance(asset, chain, address)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun existentialDepositUnavailable(asset: CoreAsset) =
        TransferValidationResult.ExistentialDepositUnavailable(asset.symbol.uppercase())

    private companion object {
        val EVM_ECOSYSTEMS = setOf(Ecosystem.EthereumBased, Ecosystem.Ethereum)
    }


    private fun performChecks(
        checks: Map<TransferValidationResult, Boolean>,
        confirmedValidations: List<TransferValidationResult>,
        skipEdValidation: Boolean = false
    ): TransferValidationResult {
        // Availability is an authorization prerequisite, never a dismissible warning.
        if (checks[TransferValidationResult.BalanceUnavailable] == true) return TransferValidationResult.BalanceUnavailable
        checks.filterNot { (result, _) ->
            result in confirmedValidations || skipEdValidation && result.isExistentialDepositWarning
        }.forEach { (result, condition) ->
            if (condition) return result
        }
        return TransferValidationResult.Valid
    }
}
