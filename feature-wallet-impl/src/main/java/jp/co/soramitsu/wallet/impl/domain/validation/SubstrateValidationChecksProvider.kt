package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Asset as CoreAsset
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks as tokenAmountFromPlanks
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.FEE_RESERVE_TOLERANCE
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.math.BigInteger

class SubstrateValidationChecksProvider(
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val chainsRepository: ChainsRepository,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val polkaswapInteractor: PolkaswapInteractor

    ) : ValidationChecksProvider {
    override suspend fun provide(
        amountInPlanks: BigInteger,
        asset: Asset,
        destinationAddress: String,
        fee: BigInteger
    ): Map<TransferValidationResult, Boolean> {
        val transferable = asset.transferableInPlanks
            ?: return mapOf(TransferValidationResult.BalanceUnavailable to true)
        val available = asset.sendAvailableInPlanks
            ?: return mapOf(TransferValidationResult.BalanceUnavailable to true)
        val chainId = asset.token.configuration.chainId
        val chain = chainsRepository.getChain(chainId)

        val metaAccount = accountRepository.getSelectedMetaAccount()
        val senderAddress = metaAccount.address(chain)!!

        val totalDestinationBalanceInPlanks = kotlin.runCatching {
            walletRepository.getTotalBalance(asset.token.configuration, chain, destinationAddress)
        }.onFailure { if (it is CancellationException) throw it }.getOrNull().orZero()
        val totalDestinationBalanceDecimal =
            asset.token.configuration.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()

        val existentialDepositResult = existentialDepositUseCase(asset.token.configuration)
            .onFailure { if (it is CancellationException) throw it }
        val existentialDeposit = existentialDepositResult.getOrNull().orZero()
        val edFormatted = existentialDeposit.formatCryptoDetailFromPlanks(asset.token.configuration)
        val existentialDepositDecimal =
            asset.token.configuration.amountFromPlanks(existentialDeposit).orZero()


        val amountDecimal = asset.token.configuration.amountFromPlanks(amountInPlanks).orZero()

        val shouldConsiderTip = asset.token.configuration.isUtility
        val tip = if (shouldConsiderTip) walletConstants.tip(chain.id).orZero() else BigInteger.ZERO

        val destinationResultFormatted =
            (totalDestinationBalanceDecimal + amountDecimal).formatCryptoDetail(asset.token.configuration.symbol)
        val destinationExtra =
            existentialDepositDecimal - (totalDestinationBalanceDecimal + amountDecimal)
        val destinationExtraFormatted =
            destinationExtra.formatCryptoDetail(asset.token.configuration.symbol)


        return when {
            asset.token.configuration.type == ChainAssetType.Equilibrium -> {
                val destinationAccountId = chain.accountIdOf(destinationAddress)
                getEquilibriumValidationChecks(
                    asset,
                    destinationAccountId,
                    chain,
                    senderAddress,
                    amountInPlanks,
                    fee,
                    tip,
                    existentialDepositResult,
                    existentialDepositDecimal,
                    asset.token.configuration.symbol
                )
            }

            asset.token.configuration.isUtility -> {
                val resultedBalance = (asset.freeInPlanks.positiveOrNull()
                    ?: transferable) - (amountInPlanks + fee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + fee + tip > available),
                    existentialDepositUnknownCheck(asset.token.configuration to existentialDepositResult),
                    TransferValidationResult.ExistentialDepositWarning(edFormatted) to (resultedBalance < existentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        edFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < existentialDepositDecimal)
                )
            }

            asset.token.configuration.currencyId == bokoloCashTokenId -> { // xorlessTransfer
                val utilityAsset: Asset? = chain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(chain)!!,
                        it,
                        chain.minSupportedVersion
                    )
                }


                val utilityAvailable = utilityAsset?.transferableInPlanks
                    ?: return mapOf(TransferValidationResult.BalanceUnavailable to true)
                val feeRequiredTokenInPlanks = if (utilityAvailable < fee) {
                    val swapDetails = polkaswapInteractor.calcDetails(
                        availableDexPaths = listOf(0),
                        tokenFrom = asset,
                        tokenTo = utilityAsset,
                        amount = utilityAsset.token.tokenAmountFromPlanks(fee),
                        desired = WithDesired.OUTPUT,
                        slippageTolerance = 1.5,
                        market = Market.SMART
                    ).getOrThrow()
                    val quote = requireNotNull(swapDetails) { "Fee conversion quote is unavailable" }
                    asset.token.planksFromAmount(quote.amount * FEE_RESERVE_TOLERANCE)
                } else {
                    BigInteger.ZERO
                }

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + feeRequiredTokenInPlanks > available),
                    existentialDepositUnknownCheck(asset.token.configuration to existentialDepositResult),
                    TransferValidationResult.ExistentialDepositWarning(edFormatted) to (transferable - amountInPlanks - feeRequiredTokenInPlanks < existentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        edFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < existentialDepositDecimal)
                )
            }

            else -> {
                val utilityAsset = chain.utilityAsset?.let {
                    walletRepository.getAsset(
                        metaAccount.id,
                        metaAccount.accountId(chain)!!,
                        it,
                        chain.minSupportedVersion
                    )
                }
                val utilityAssetBalance = utilityAsset?.transferableInPlanks
                    ?: return mapOf(TransferValidationResult.BalanceUnavailable to true)
                val utilityAssetExistentialDepositResult = chain.utilityAsset?.let { existentialDepositUseCase(it).onFailure { error -> if (error is CancellationException) throw error } }
                val utilityAssetExistentialDeposit = utilityAssetExistentialDepositResult?.getOrNull().orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let {
                    utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it)
                }.orEmpty()

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > available),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    existentialDepositUnknownCheck(
                        asset.token.configuration to existentialDepositResult,
                        chain.utilityAsset to utilityAssetExistentialDepositResult
                    ),
                    TransferValidationResult.ExistentialDepositWarning(edFormatted) to (transferable - amountInPlanks < existentialDeposit),
                    TransferValidationResult.UtilityExistentialDepositWarning(utilityEdFormatted) to (utilityAssetBalance - (fee + tip) < utilityAssetExistentialDeposit),
                    TransferValidationResult.DeadRecipient(
                        destinationResultFormatted,
                        edFormatted,
                        destinationExtraFormatted
                    ) to (totalDestinationBalanceDecimal + amountDecimal < existentialDepositDecimal)
                )
            }
        }
    }
    private suspend fun getEquilibriumValidationChecks(
        originAsset: Asset,
        destinationAccountId: ByteArray,
        originChain: Chain,
        originAddress: String,
        amountInPlanks: BigInteger,
        originFee: BigInteger,
        tip: BigInteger,
        originAssetExistentialDepositResult: Result<BigInteger>,
        destinationExistentialDepositDecimal: BigDecimal,
        destinationAssetSymbol: String?
    ): Map<TransferValidationResult, Boolean> {
        val originAssetConfig = originAsset.token.configuration
        val originAssetExistentialDeposit = originAssetExistentialDepositResult.getOrNull().orZero()

        val destinationAccountInfo =
            walletRepository.getEquilibriumAccountInfo(originAssetConfig, destinationAccountId)
        val originAccountInfo = walletRepository.getEquilibriumAccountInfo(
            originAssetConfig,
            originChain.accountIdOf(originAddress)
        )
        val assetRates = walletRepository.getEquilibriumAssetRates(originAssetConfig)

        val originNewTotal =
            originAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
                val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

                val newAmount = when {
                    originAssetConfig.isUtility -> {
                        if (eqAssetId == originAssetConfig.currency) {
                            if (amount < amountInPlanks + originFee + tip) return mapOf(
                                TransferValidationResult.InsufficientBalance to true
                            )
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
                val rateDecimal =
                    originAsset.token.configuration.amountFromPlanks(tokenRateInPlanks)

                amountDecimal * rateDecimal
            }.orZero()

        val destinationNewTotal =
            destinationAccountInfo?.data?.balances?.entries?.sumByBigDecimal { (eqAssetId, amount) ->
                val newAmount =
                    if (eqAssetId == originAssetConfig.currency) amount + amountInPlanks else amount
                val tokenRateInPlanks = assetRates[eqAssetId]?.price.orZero()

                val amountDecimal = originAsset.token.configuration.amountFromPlanks(newAmount)
                val rateDecimal =
                    originAsset.token.configuration.amountFromPlanks(tokenRateInPlanks)

                amountDecimal * rateDecimal
            }.orZero()

        val originNewTotalInPlanks =
            originAsset.token.configuration.planksFromAmount(originNewTotal)

        val originAssetEdFormatted =
            originAssetExistentialDeposit.formatCryptoDetailFromPlanks(originAsset.token.configuration)
        val destinationEdFormatted =
            destinationExistentialDepositDecimal.formatCryptoDetail(destinationAssetSymbol)
        val destinationResultFormatted =
            destinationNewTotal.formatCryptoDetail(destinationAssetSymbol)
        val destinationExtra = destinationExistentialDepositDecimal - destinationNewTotal
        val destinationExtraFormatted = destinationExtra.formatCryptoDetail(destinationAssetSymbol)

        return mapOf(
            existentialDepositUnknownCheck(originAssetConfig to originAssetExistentialDepositResult),
            TransferValidationResult.ExistentialDepositWarning(originAssetEdFormatted) to (originNewTotalInPlanks < originAssetExistentialDeposit),
            TransferValidationResult.DeadRecipient(
                destinationResultFormatted,
                destinationEdFormatted,
                destinationExtraFormatted
            ) to (destinationNewTotal < destinationExistentialDepositDecimal)
        )
    }
}

/**
 * Comes before the existential deposit checks of a validation: those read an unknown minimum as zero, so they
 * cannot fire for it, and the user is told instead that the minimum was not checked.
 */
internal fun existentialDepositUnknownCheck(
    vararg existentialDeposits: Pair<CoreAsset?, Result<BigInteger>?>
): Pair<TransferValidationResult, Boolean> {
    existentialDeposits.forEach { (_, result) ->
        (result?.exceptionOrNull() as? CancellationException)?.let { throw it }
    }
    val unknownSymbols = existentialDeposits
        .filter { (asset, result) -> asset != null && result?.isFailure == true }
        .map { (asset, _) -> asset!!.symbol.uppercase() }
        .distinct()

    return TransferValidationResult.ExistentialDepositUnknown(unknownSymbols.joinToString()) to unknownSymbols.isNotEmpty()
}
