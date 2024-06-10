package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

private const val CROSS_CHAIN_ED_SAFE_TRANSFER_MULTIPLIER = 1.1

class QuickInputsUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val chainsRepository: ChainsRepository,
    private val walletConstants: WalletConstants,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val xcmInteractor: XcmInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
) : QuickInputsUseCase {

    private val inputValues = listOf(1.0, 0.75, 0.5, 0.25)

    override suspend fun calculateStakingQuickInputs(
        chainId: ChainId,
        assetId: String,
        calculateAvailableAmount: suspend () -> BigDecimal,
        calculateFee: suspend (amount: BigInteger) -> BigInteger
    ): Map<Double, BigDecimal> =
        withContext(
            coroutineContext
        ) {
            val chainDeferred = async { chainsRepository.getChain(chainId) }
            val allAmountDeferred = async { calculateAvailableAmount() }
            val chain = chainDeferred.await()
            val chainAsset = chain.assetsById[assetId] ?: return@withContext emptyMap()
            val allAmount = allAmountDeferred.await()

            val quickAmounts = inputValues.map { input ->
                async {
                    val amountToTransfer = (allAmount * input.toBigDecimal()).setScale(
                        chainAsset.precision,
                        RoundingMode.HALF_DOWN
                    )
                    val amountInPlanks = chainAsset.planksFromAmount(amountToTransfer)
                    val feeInPlanks =
                        runCatching { calculateFee(amountInPlanks) }.getOrNull().orZero()
                    val fee = chainAsset.amountFromPlanks(feeInPlanks)

                    val quickAmountWithoutExtraPays = amountToTransfer - fee
                    quickAmountWithoutExtraPays.coerceAtLeast(BigDecimal.ZERO)
                }
            }.awaitAll()

            inputValues.zip(quickAmounts).toMap()
        }

    override suspend fun calculatePolkaswapQuickInputs(
        assetIdFrom: String,
        assetIdTo: String
    ): Map<Double, BigDecimal> = withContext(coroutineContext) {
        val chainDeferred =
            async { chainsRepository.getChain(polkaswapInteractor.polkaswapChainId) }
        val currentAccountDeferred = async { accountRepository.getSelectedMetaAccount() }

        val chain = chainDeferred.await()
        val currentAccount = currentAccountDeferred.await()

        val utilityChainAsset = chain.utilityAsset ?: return@withContext emptyMap()
        val chainAssetFrom = chain.assetsById[assetIdFrom] ?: return@withContext emptyMap()
        val chainAssetTo = chain.assetsById[assetIdTo] ?: return@withContext emptyMap()
        val accountId = currentAccount.accountId(chain) ?: return@withContext emptyMap()

        val assetFromDeferred =
            async { walletRepository.getAsset(currentAccount.id, accountId, chainAssetFrom, null) }

        val assetFrom = assetFromDeferred.await() ?: return@withContext emptyMap()

        val quickAmounts = inputValues.map { input ->
            async {
                val amountToTransfer = (assetFrom.transferable * input.toBigDecimal()).setScale(
                    chainAssetFrom.precision,
                    RoundingMode.HALF_DOWN
                )
                val quickAmountWithoutExtraPays = if (chainAssetFrom.id == utilityChainAsset.id) {
                    val feeInPlanks = polkaswapInteractor.estimateSwapFee(
                        1,
                        requireNotNull(chainAssetFrom.currencyId),
                        requireNotNull(chainAssetTo.currencyId),
                        chainAssetFrom.planksFromAmount(amountToTransfer),
                        Market.SMART,
                        WithDesired.INPUT
                    )
                    amountToTransfer - chainAssetFrom.amountFromPlanks(feeInPlanks)
                } else {
                    amountToTransfer
                }
                quickAmountWithoutExtraPays.coerceAtLeast(BigDecimal.ZERO)
            }
        }.awaitAll()

        inputValues.zip(quickAmounts).toMap()
    }

    override suspend fun calculateXcmTransfersQuickInputs(
        originChainId: ChainId,
        destinationChainId: ChainId,
        assetId: String
    ): Map<Double, BigDecimal> = withContext(coroutineContext) {
        val originChainDeferred = async { chainsRepository.getChain(originChainId) }

        val currentAccountDeferred = async { accountRepository.getSelectedMetaAccount() }

        val originChain = originChainDeferred.await()
        val currentAccount = currentAccountDeferred.await()

        val chainAsset = originChain.assetsById[assetId] ?: return@withContext emptyMap()
        val accountId = currentAccount.accountId(originChain) ?: return@withContext emptyMap()

        val assetDeferred =
            async { walletRepository.getAsset(currentAccount.id, accountId, chainAsset, null) }

        val asset = assetDeferred.await() ?: return@withContext emptyMap()

        val existentialDepositDeferred =
            async { existentialDepositUseCase(asset.token.configuration) }
        val existentialDepositInPlanks = existentialDepositDeferred.await()
        val existentialDepositDecimal = asset.token.amountFromPlanks(existentialDepositInPlanks)
        val existentialDepositWithExtra =
            existentialDepositDecimal * CROSS_CHAIN_ED_SAFE_TRANSFER_MULTIPLIER.toBigDecimal()

        val transferable = maxOf(BigDecimal.ZERO, asset.transferable - existentialDepositWithExtra)

        val quickAmounts = inputValues.map { input ->
            async {
                val destinationFee = xcmInteractor.getDestinationFee(
                    destinationChainId = destinationChainId,
                    tokenConfiguration = chainAsset
                ) ?: BigDecimal.ZERO

                val originFee = xcmInteractor.getOriginFee(
                    originNetworkId = originChainId,
                    destinationNetworkId = destinationChainId,
                    asset = asset.token.configuration,
                    amount = transferable * input.toBigDecimal() + destinationFee
                ) ?: BigDecimal.ZERO

                val quickAmountWithoutExtraPays =
                    (transferable * input.toBigDecimal()).setScale(
                        chainAsset.precision,
                        RoundingMode.HALF_DOWN
                    ) - destinationFee - originFee

                quickAmountWithoutExtraPays.coerceAtLeast(BigDecimal.ZERO)
            }
        }.awaitAll()

        inputValues.zip(quickAmounts).toMap()
    }

    override suspend fun calculateTransfersQuickInputs(
        chainId: ChainId,
        assetId: String
    ): Map<Double, BigDecimal> =
        withContext(
            coroutineContext
        ) {
            val chainDeferred = async { chainsRepository.getChain(chainId) }
            val currentAccountDeferred = async { accountRepository.getSelectedMetaAccount() }
            val tipDeferred = async { walletConstants.tip(chainId).orZero() }

            val chain = chainDeferred.await()
            val currentAccount = currentAccountDeferred.await()
            val tip = tipDeferred.await()

            val utilityAsset = chain.utilityAsset ?: return@withContext emptyMap()
            val chainAsset = chain.assetsById[assetId] ?: return@withContext emptyMap()
            val accountId = currentAccount.accountId(chain) ?: return@withContext emptyMap()
            val selfAddress = currentAccount.address(chain) ?: return@withContext emptyMap()

            val assetDeferred =
                async { walletRepository.getAsset(currentAccount.id, accountId, chainAsset, null) }
            val asset = assetDeferred.await() ?: return@withContext emptyMap()

            val tipAmount = utilityAsset.amountFromPlanks(tip)
            val utilityTipReserve =
                if (asset.token.configuration.isUtility) tipAmount else BigDecimal.ZERO
            val allAmount = asset.transferable
//            val slippageTolerance = BigDecimal("1.35")

            val quickAmounts = inputValues.map { input ->
                async {
                    val amountToTransfer = (allAmount * input.toBigDecimal()).setScale(
                        asset.token.configuration.precision,
                        RoundingMode.HALF_DOWN
                    ) - utilityTipReserve

                    val transfer = Transfer(
                        recipient = selfAddress,
                        sender = selfAddress,
                        amount = amountToTransfer,
                        chainAsset = asset.token.configuration
                    )

                    val utilityFeeReserve = if (asset.token.configuration.isUtility) {
                        runCatching {
                            walletRepository.getTransferFee(
                                chain,
                                transfer
                            ).feeAmount
                        }.getOrNull().orZero()
                    } else {
                        BigDecimal.ZERO
                    }

                    val quickAmountWithoutExtraPays =
                        amountToTransfer - utilityFeeReserve//* slippageTolerance)

                    quickAmountWithoutExtraPays.coerceAtLeast(BigDecimal.ZERO)
//                    if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
//                        BigDecimal.ZERO
//                    } else {
//                        quickAmountWithoutExtraPays.setScale(5, RoundingMode.HALF_DOWN)
//                    }
                }
            }.awaitAll()

            inputValues.zip(quickAmounts).toMap()
        }

    private suspend fun calculateQuickInputs() {

    }
}