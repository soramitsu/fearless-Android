package jp.co.soramitsu.wallet.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class QuickInputsUseCaseImpl(
    private val walletRepository: WalletRepository,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val chainsRepository: ChainsRepository,
    private val walletConstants: WalletConstants,
    private val coroutineContext: CoroutineContext = Dispatchers.Default,
): QuickInputsUseCase {
    val inputValues = listOf(1.0, 0.75, 0.5, 0.25)
    override suspend fun calculateStakingQuickInputs(chainId: ChainId, assetId: String, calculateFee: suspend () -> BigInteger): Map<Double, BigDecimal> =
        withContext(
            coroutineContext
        ) {
            val chainDeferred = async { chainsRepository.getChain(chainId) }
            val currentAccountDeferred = async { accountRepository.getSelectedMetaAccount() }

            val chain = chainDeferred.await()
            val currentAccount = currentAccountDeferred.await()

            val chainAsset = chain.assetsById[assetId] ?: return@withContext emptyMap()
            val accountId = currentAccount.accountId(chain) ?: return@withContext emptyMap()

            val assetDeferred =
                async { walletRepository.getAsset(currentAccount.id, accountId, chainAsset, null) }
            val asset = assetDeferred.await() ?: return@withContext emptyMap()

            val allAmount = asset.availableForStaking
            val slippageTolerance = BigDecimal("1.35")


            val quickAmounts = inputValues.map { input ->
                async {
                    val amountToTransfer = (allAmount * input.toBigDecimal())

                    val feeInPlanks = runCatching { calculateFee() }.getOrNull().orZero()
                    val fee = chainAsset.amountFromPlanks(feeInPlanks)

                    val quickAmountWithoutExtraPays =
                        amountToTransfer - (fee * slippageTolerance)

                    if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
                        BigDecimal.ZERO
                    } else {
                        quickAmountWithoutExtraPays.setScale(5, RoundingMode.HALF_DOWN)
                    }
                }
            }.awaitAll()

            inputValues.zip(quickAmounts).toMap()
        }

    override suspend fun calculateTransfersQuickInputs(chainId: ChainId, assetId: String): Map<Double, BigDecimal> =
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
            val slippageTolerance = BigDecimal("1.35")

            val quickAmounts = inputValues.map { input ->
                async {
                    val amountToTransfer = (allAmount * input.toBigDecimal()) - utilityTipReserve

                    val transfer = Transfer(
                        recipient = selfAddress,
                        sender = selfAddress,
                        amount = amountToTransfer,
                        chainAsset = asset.token.configuration
                    )

                    val utilityFeeReserve = if (asset.token.configuration.isUtility) {
                        runCatching { walletRepository.getTransferFee(chain, transfer).feeAmount }.getOrNull().orZero()
                    } else {
                        BigDecimal.ZERO
                    }

                    val quickAmountWithoutExtraPays =
                        amountToTransfer - (utilityFeeReserve * slippageTolerance)

                    if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
                        BigDecimal.ZERO
                    } else {
                        quickAmountWithoutExtraPays.setScale(5, RoundingMode.HALF_DOWN)
                    }
                }
            }.awaitAll()

            inputValues.zip(quickAmounts).toMap()
        }

    private suspend fun calculateQuickInputs() {

    }
}