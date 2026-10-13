package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.model.AssetMetadataDescriptor
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope

@SuppressLint("LogNotTimber")
class TonBalanceLoader(
    chain: Chain,
    private val tonSyncDataRepository: TonSyncDataRepository,
    private val chainsRepository: ChainsRepository,
    private val persistDiscoveredAssets: suspend (List<Asset>) -> Unit = {},
    private val metadataDescriptorStore: AssetMetadataDescriptorStore? = null,
    private val scanStateStore: NetworkScanStateStore? = null
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "TonBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> =
        supervisorScope {
            metaAccounts.filter { it.tonPublicKey != null }
                .map { metaAccount ->
                    async { loadAccountBalances(metaAccount).map(TonAssetBalanceUpdate::balance) }
                }
                .awaitAll()
                .flatten()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        return trigger.onStart { emit(null) }.flatMapLatest { triggeredChainId ->
            channelFlow {
                val specificChainTriggered = triggeredChainId != null
                if (specificChainTriggered && triggeredChainId != chain.id) return@channelFlow

                loadAccountBalances(metaAccount).forEach { update ->
                    send(BalanceLoaderAction.UpdateOrInsertBalance(update.balance, update.asset))
                }
            }
        }
    }

    private suspend fun loadAccountBalances(metaAccount: MetaAccount): List<TonAssetBalanceUpdate> {
        val publicKey = metaAccount.tonPublicKey ?: return emptyList()
        val accountId = publicKey.tonAccountId(chain.isTestNet)
        scanStateStore?.scanStarted(metaAccount.id, chain.id, AssetDiscoveryCoverage.Complete)

        val (accountDataResult, jettonBalancesResult) = coroutineScope {
            val accountData = async {
                requestResult { tonSyncDataRepository.getAccountData(chain, accountId) }
            }
            val jettons = async {
                requestResult { tonSyncDataRepository.getJettonBalances(chain, accountId) }
            }
            accountData.await() to jettons.await()
        }

        val failures = mutableListOf<Throwable>()
        accountDataResult.exceptionOrNull()?.let(failures::add)
        jettonBalancesResult.exceptionOrNull()?.let(failures::add)

        val currentAssets = chainsRepository.getChain(chain.id).assets
        val rawJettons = jettonBalancesResult.getOrNull()?.balances.orEmpty()
        val parsedJettonBalances = rawJettons.groupBy { it.jetton.address }
            .mapValues { (masterAddress, balances) ->
                balances.map { balance ->
                    balance.balance.toUnsignedBigIntegerOrNull().also { parsed ->
                        if (parsed == null) {
                            failures += IllegalArgumentException(
                                "Invalid Jetton balance for $masterAddress"
                            )
                        }
                    }
                }.takeIf { values -> values.all { it != null } }
                    ?.filterNotNull()
                    ?.fold(BigInteger.ZERO, BigInteger::add)
            }

        val discoveredAssets = rawJettons
            .distinctBy { it.jetton.address }
            .filter { balance ->
                parsedJettonBalances[balance.jetton.address]?.signum() == 1 &&
                    currentAssets.none { it.id == balance.jetton.address }
            }
            .map { it.toDetectedAsset() }

        val persistedDiscoveredAssets = if (discoveredAssets.isEmpty()) {
            emptyList()
        } else {
            try {
                persistDiscoveredAssets(discoveredAssets)
                rawJettons.distinctBy { it.jetton.address }.forEach { balance ->
                    metadataDescriptorStore?.record(
                        chain.assetKey(balance.jetton.address),
                        AssetMetadataDescriptor(
                            trust = balance.metadataTrust(),
                            source = AssetMetadataSource.Indexer
                        )
                    )
                }
                discoveredAssets
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
                log("failed to persist detected Jettons: $error")
                emptyList()
            }
        }

        val availableAssets = (currentAssets + persistedDiscoveredAssets).distinctBy(Asset::id)
        val updates = buildList {
            accountDataResult.getOrNull()?.let { accountData ->
                availableAssets.firstOrNull(Asset::isUtility)?.let { nativeAsset ->
                    add(
                        TonAssetBalanceUpdate(
                            balance = nativeAsset.balanceUpdate(
                                metaId = metaAccount.id,
                                accountId = publicKey,
                                balance = accountData.balance.toBigInteger()
                            ),
                            asset = nativeAsset
                        )
                    )
                }
            }

            if (jettonBalancesResult.isSuccess) {
                availableAssets.filter { it.type == ChainAssetType.Jetton }.forEach { asset ->
                    val balance = parsedJettonBalances[asset.id]
                        ?: if (asset.id in parsedJettonBalances) return@forEach else BigInteger.ZERO
                    add(
                        TonAssetBalanceUpdate(
                            balance = asset.balanceUpdate(metaAccount.id, publicKey, balance),
                            asset = asset
                        )
                    )
                }
            }
        }

        val failure = failures.firstOrNull()
        if (failure == null) {
            scanStateStore?.scanSucceeded(
                metaAccount.id,
                chain.id,
                AssetDiscoveryCoverage.Complete
            )
        } else {
            scanStateStore?.scanFailed(
                metaAccount.id,
                chain.id,
                AssetDiscoveryCoverage.Complete,
                failure.message ?: failure::class.simpleName
            )
            log("TON asset discovery was partial: $failure")
        }

        return updates
    }

    private fun Asset.balanceUpdate(
        metaId: Long,
        accountId: ByteArray,
        balance: BigInteger
    ) = AssetBalanceUpdateItem(
        metaId = metaId,
        chainId = chain.id,
        accountId = accountId,
        id = id,
        freeInPlanks = balance
    )

    private fun JettonBalance.toDetectedAsset(): Asset {
        val verified = metadataTrust() == AssetMetadataTrust.Verified
        return Asset(
            id = jetton.address,
            name = jetton.name.takeIf(String::isNotBlank),
            symbol = jetton.symbol.takeIf(String::isNotBlank) ?: jetton.address,
            iconUrl = jetton.image,
            chainId = chain.id,
            chainName = chain.name,
            chainIcon = chain.icon,
            isTestNet = chain.isTestNet,
            // A trusted price id is the canonical master address, never the ticker.
            priceId = jetton.address.takeIf { verified },
            precision = jetton.decimals,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = ChainAssetType.Jetton,
            currencyId = jetton.address,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
    }

    private fun JettonBalance.metadataTrust(): AssetMetadataTrust {
        val verified = jetton.verification.equals("whitelist", ignoreCase = true) ||
            jetton.verification.equals("verified", ignoreCase = true)
        return if (verified) AssetMetadataTrust.Verified else AssetMetadataTrust.Unverified
    }

    private suspend fun <T> requestResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("TON request failed: $error")
            Result.failure(error)
        }
    }

    private fun String.toUnsignedBigIntegerOrNull(): BigInteger? =
        runCatching { BigInteger(this) }.getOrNull()?.takeIf { it.signum() >= 0 }

    private fun log(message: String) {
        runCatching { Log.d(tag, message) }
    }

    private data class TonAssetBalanceUpdate(
        val balance: AssetBalanceUpdateItem,
        val asset: Asset
    )
}
