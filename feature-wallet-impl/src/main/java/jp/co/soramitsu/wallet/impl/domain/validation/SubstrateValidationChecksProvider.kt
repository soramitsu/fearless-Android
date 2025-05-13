package jp.co.soramitsu.wallet.impl.domain.validation

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.positiveOrNull
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.ChainAssetType
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
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.FEE_RESERVE_TOLERANCE
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
        val chainId = asset.token.configuration.chainId
        val chain = chainsRepository.getChain(chainId)

        val metaAccount = accountRepository.getSelectedMetaAccount()
        val senderAddress = metaAccount.address(chain)!!

        val totalDestinationBalanceInPlanks = kotlin.runCatching {
            walletRepository.getTotalBalance(asset.token.configuration, chain, destinationAddress)
        }.getOrNull().orZero()
        val totalDestinationBalanceDecimal =
            asset.token.configuration.amountFromPlanks(totalDestinationBalanceInPlanks).orZero()

        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)
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
                    existentialDepositDecimal,
                    asset.token.configuration.symbol
                )
            }

            asset.token.configuration.isUtility -> {
                val resultedBalance = (asset.freeInPlanks.positiveOrNull()
                    ?: asset.transferableInPlanks) - (amountInPlanks + fee + tip)

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + fee + tip > asset.sendAvailableInPlanks),
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


                val feeRequiredTokenInPlanks =
                    if (utilityAsset?.transferableInPlanks.orZero() < fee) {
                        val swapDetails = utilityAsset?.let {
                            polkaswapInteractor.calcDetails(
                                availableDexPaths = listOf(0),
                                tokenFrom = asset,
                                tokenTo = utilityAsset,
                                amount = asset.token.configuration.amountFromPlanks(fee),
                                desired = WithDesired.OUTPUT,
                                slippageTolerance = 1.5,
                                market = Market.SMART
                            )
                        }?.onFailure {
//                        if (it is InsufficientLiquidityException) {
//                            return TransferValidationResult.InsufficientBalance

//                        }
                        }
                        swapDetails?.getOrNull()?.amount?.let {
                            utilityAsset.token.planksFromAmount(
                                it * FEE_RESERVE_TOLERANCE
                            )
                        }
                    } else {
                        null
                    }

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks + feeRequiredTokenInPlanks.orZero() > asset.sendAvailableInPlanks),
                    TransferValidationResult.ExistentialDepositWarning(edFormatted) to (asset.transferableInPlanks - amountInPlanks - feeRequiredTokenInPlanks.orZero() < existentialDeposit),
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
                val utilityAssetBalance = utilityAsset?.transferableInPlanks.orZero()
                val utilityAssetExistentialDeposit =
                    chain.utilityAsset?.let { existentialDepositUseCase(it) }.orZero()
                val utilityEdFormatted = utilityAsset?.token?.configuration?.let {
                    utilityAssetExistentialDeposit.formatCryptoDetailFromPlanks(it)
                }.orEmpty()

                mapOf(
                    TransferValidationResult.InsufficientBalance to (amountInPlanks > asset.sendAvailableInPlanks),
                    TransferValidationResult.InsufficientUtilityAssetBalance to (fee + tip > utilityAssetBalance),
                    TransferValidationResult.ExistentialDepositWarning(edFormatted) to (asset.transferableInPlanks - amountInPlanks < existentialDeposit),
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
        destinationExistentialDepositDecimal: BigDecimal,
        destinationAssetSymbol: String?
    ): Map<TransferValidationResult, Boolean> {
        val originAssetConfig = originAsset.token.configuration
        val originAssetExistentialDeposit = existentialDepositUseCase(originAssetConfig)

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
            TransferValidationResult.ExistentialDepositWarning(originAssetEdFormatted) to (originNewTotalInPlanks < originAssetExistentialDeposit),
            TransferValidationResult.DeadRecipient(
                destinationResultFormatted,
                destinationEdFormatted,
                destinationExtraFormatted
            ) to (destinationNewTotal < destinationExistentialDepositDecimal)
        )
    }
}