package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinAccountBalanceSyncResult
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBalanceSync
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.runtime.ext.normalizedBitcoinAddress
import jp.co.soramitsu.runtime.ext.universalWalletBitcoinIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.BalanceUpdateTrigger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CancellationException
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator

@SuppressLint("LogNotTimber")
class BitcoinBalanceLoader(
    chain: Chain,
    private val bitcoinIndexerClient: BitcoinIndexerClient,
    private val bitcoinBalanceSync: BitcoinBalanceSync? = null,
    private val accountRepository: AccountRepository? = null,
    private val scanStateStore: NetworkScanStateStore? = null
) : BalanceLoader(chain) {

    private val trigger = BalanceUpdateTrigger.observe()
    private val tag = "BitcoinBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val balancesDeferred = metaAccounts.map { metaAccount ->
                async {
                    loadNativeBalance(metaAccount)
                }
            }

            balancesDeferred.awaitAll().filterNotNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        return trigger.onStart { emit(null) }.flatMapLatest { triggeredChainId ->
            channelFlow {
                val specificChainTriggered = triggeredChainId != null
                val currentChainTriggered = triggeredChainId == chain.id

                if (specificChainTriggered && currentChainTriggered.not()) return@channelFlow

                coroutineScope {
                    val balance = loadNativeBalance(metaAccount) ?: return@coroutineScope
                    val asset = nativeBitcoinAsset() ?: return@coroutineScope
                    send(BalanceLoaderAction.UpdateOrInsertBalance(balance, asset))
                }
            }
        }
    }

    private suspend fun loadNativeBalance(metaAccount: MetaAccount): AssetBalanceUpdateItem? {
        val asset = nativeBitcoinAsset() ?: return null
        val accountId = metaAccount.accountId(chain) ?: return null
        val address = metaAccount.address(chain)?.let(chain::normalizedBitcoinAddress) ?: return null
        val network = chain.universalWalletBitcoinIndexerNetwork() ?: return null
        val baseUrl = chain.externalApi?.history?.url

        val entropy = try {
            accountRepository?.getChainAccountSecrets(metaAccount.id, chain.id)
                ?.get(ChainAccountSecrets.Entropy)
        } catch (error: Exception) {
            runCatching { Log.d(tag, "bitcoin root secret unavailable: $error") }
            null
        }
        val hasGapDiscovery = entropy != null && bitcoinBalanceSync != null
        val coverage = if (hasGapDiscovery) AssetDiscoveryCoverage.Complete else AssetDiscoveryCoverage.Limited
        scanStateStore?.scanStarted(metaAccount.id, chain.id, coverage)

        val balance = try {
            if (hasGapDiscovery) {
                val mnemonic = MnemonicCreator.fromEntropy(requireNotNull(entropy)).words
                val derivationNetwork = if (chain.isTestNet) {
                    BitcoinKeyDerivation.Network.Testnet
                } else {
                    BitcoinKeyDerivation.Network.Mainnet
                }
                requireNotNull(bitcoinBalanceSync).accountBalance(
                    mnemonic = mnemonic,
                    network = derivationNetwork,
                    baseUrl = baseUrl
                ).validatedTotalSats()
            } else {
                bitcoinIndexerClient.address(
                    address = address,
                    network = network,
                    baseUrl = baseUrl
                ).validatedTotalSats(expectedAddress = address)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { Log.d(tag, "bitcoin balance scan failed: $error") }
            scanStateStore?.scanFailed(
                metaAccount.id,
                chain.id,
                coverage,
                error.message ?: error::class.simpleName
            )
            null
        } ?: return null

        scanStateStore?.scanSucceeded(metaAccount.id, chain.id, coverage)

        return AssetBalanceUpdateItem(
            metaId = metaAccount.id,
            chainId = chain.id,
            accountId = accountId,
            id = asset.id,
            freeInPlanks = balance
        )
    }

    private fun nativeBitcoinAsset(): Asset? {
        return chain.assets.firstOrNull { asset ->
            asset.isNative == true && asset.symbol.equals(BITCOIN_SYMBOL, ignoreCase = true)
        } ?: chain.assets.firstOrNull { asset ->
            asset.isUtility && asset.symbol.equals(BITCOIN_SYMBOL, ignoreCase = true)
        }
    }

    private fun BitcoinAccountBalanceSyncResult.validatedTotalSats(): BigInteger {
        require(confirmedSats >= 0 && mempoolSats >= 0 && totalSats >= 0) {
            INVALID_BALANCE_PAYLOAD
        }
        val total = BigInteger.valueOf(confirmedSats).add(BigInteger.valueOf(mempoolSats))
        require(total == BigInteger.valueOf(totalSats)) { INVALID_BALANCE_PAYLOAD }
        return total
    }

    private fun BitcoinEsploraAddress.validatedTotalSats(expectedAddress: String): BigInteger {
        require(address == expectedAddress) { INVALID_BALANCE_PAYLOAD }
        val confirmed = chainStats.validatedNetSats()
        val mempool = mempoolStats.validatedNetSats()
        require(confirmed.signum() >= 0 && mempool.signum() >= 0) { INVALID_BALANCE_PAYLOAD }

        return confirmed.add(mempool)
    }

    private fun BitcoinEsploraStats.validatedNetSats(): BigInteger {
        require(
            fundedTxoCount >= 0 &&
                fundedTxoSum >= 0 &&
                spentTxoCount >= 0 &&
                spentTxoSum >= 0 &&
                txCount >= 0
        ) { INVALID_BALANCE_PAYLOAD }
        return BigInteger.valueOf(fundedTxoSum).subtract(BigInteger.valueOf(spentTxoSum))
    }

    private companion object {
        const val BITCOIN_SYMBOL = "BTC"
        const val INVALID_BALANCE_PAYLOAD = "Invalid Bitcoin balance payload"
    }
}
