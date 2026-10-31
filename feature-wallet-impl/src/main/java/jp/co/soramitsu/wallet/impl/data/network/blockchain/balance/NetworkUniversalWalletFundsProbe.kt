package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe.Funds
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe.Reading
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBalanceSync
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinReceiveDiscovery
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSource
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendRpcEndpoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Whether an account of a password wallet's universal slot holds anything, read live (never
 * from a cache) with the clients the portfolio uses: the native coin plus every token the app
 * shows. A timeout, an error or a partial token list is [Funds.UNKNOWN], which moves no slot.
 */
class NetworkUniversalWalletFundsProbe(
    private val chainsRepository: ChainsRepository,
    private val bitcoinIndexerClient: BitcoinIndexerClient,
    private val solanaBalanceSource: SolanaBalanceSource,
    private val tonRemoteSource: TonRemoteSource,
    private val irohaToriiClient: IrohaToriiClient,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val bitcoinTimeoutMillis: Long = BITCOIN_TIMEOUT_MILLIS
) : UniversalWalletFundsProbe {

    override suspend fun probe(slot: UniversalWalletSlot, mnemonic: String, passphrase: String, publicKey: ByteArray): Funds =
        read(slot, mnemonic, passphrase, publicKey).funds

    override suspend fun read(slot: UniversalWalletSlot, mnemonic: String, passphrase: String, publicKey: ByteArray): Reading {
        val bitcoin = slot == UniversalWalletSlot.BITCOIN_MAINNET || slot == UniversalWalletSlot.BITCOIN_TESTNET
        return try {
            withTimeoutOrNull(if (bitcoin) bitcoinTimeoutMillis else timeoutMillis) {
                when (slot) {
                    UniversalWalletSlot.BITCOIN_MAINNET, UniversalWalletSlot.BITCOIN_TESTNET -> bitcoin(slot, mnemonic, passphrase)
                    UniversalWalletSlot.SOLANA -> solana(slot, publicKey)
                    UniversalWalletSlot.TON -> ton(slot, publicKey)
                    UniversalWalletSlot.TAIRA -> Reading(taira(slot, publicKey))
                }
            } ?: Reading(Funds.UNKNOWN)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            Reading(Funds.UNKNOWN)
        }
    }

    // Every address the account discovery reaches, receive and change branches, as sends spend them.
    private suspend fun bitcoin(slot: UniversalWalletSlot, mnemonic: String, passphrase: String): Reading {
        val baseUrl = chain(slot)?.externalApi?.history?.takeIf { it.type == Chain.ExternalApi.Section.Type.BITCOIN }?.url
        val balance = BitcoinBalanceSync(BitcoinReceiveDiscovery(bitcoinIndexerClient))
            .accountBalance(mnemonic, passphrase, slot.bitcoinNetwork(), baseUrl)
        return if (balance.totalSats > 0 || balance.mempoolSats != 0L) {
            Reading(Funds.FUNDED, nativeAmount(balance.totalSats.toBigInteger(), BITCOIN_DECIMALS, "BTC"))
        } else {
            Reading(Funds.EMPTY)
        }
    }

    private suspend fun solana(slot: UniversalWalletSlot, publicKey: ByteArray): Reading {
        val endpoints = chain(slot)?.let { runCatching { NativeSendRpcEndpoints.resolve(it) }.getOrNull() }.orEmpty()
        val result = solanaBalanceSource.balances(
            wallet = slot.address(publicKey),
            network = UniversalWalletRegistry.solanaMainnet,
            baseUrl = endpoints.firstOrNull(),
            includeTokenMetadata = false,
            fallbackUrls = endpoints.drop(1)
        )
        val native = result.nativeBalance.amount.toBigIntegerOrNull()?.let { nativeAmount(it, result.nativeBalance.decimals, "SOL") }
        return when {
            result.balances.any { it.amount.isPositiveAmount() } -> Reading(Funds.FUNDED, native)
            !result.tokensAvailable -> Reading(Funds.UNKNOWN)
            else -> Reading(Funds.EMPTY)
        }
    }

    private suspend fun ton(slot: UniversalWalletSlot, publicKey: ByteArray): Reading {
        val chain = chain(slot) ?: return Reading(Funds.UNKNOWN)
        val accountId = publicKey.tonAccountId(chain.isTestNet)
        val balance = tonRemoteSource.loadAccountData(chain, accountId).balance
        val native = nativeAmount(balance.toBigInteger(), TON_DECIMALS, "TON")
        if (balance > 0) return Reading(Funds.FUNDED, native)
        val jettons = tonRemoteSource.loadJettonBalances(chain, accountId)
        return when {
            jettons.balances.any { it.balance.isPositiveAmount() } -> Reading(Funds.FUNDED, native)
            jettons.partial -> Reading(Funds.UNKNOWN)
            else -> Reading(Funds.EMPTY)
        }
    }

    private suspend fun taira(slot: UniversalWalletSlot, publicKey: ByteArray): Funds {
        val baseUrl = chain(slot)?.externalApi?.history?.takeIf { it.type == Chain.ExternalApi.Section.Type.IROHA }?.url
        val address = slot.address(publicKey)
        var cursor: String? = null
        repeat(MAX_IROHA_PAGES) {
            val page = irohaToriiClient.accountAssets(address, baseUrl, IrohaToriiRoutes.MAX_LIMIT, cursor, UniversalWalletRegistry.taira)
            if (page.items.any { it.quantity.isPositiveAmount() }) return Funds.FUNDED
            cursor = page.nextCursor ?: return Funds.EMPTY
        }
        return Funds.UNKNOWN
    }

    // Only a positive native amount; token-only funds report no balance.
    private fun nativeAmount(units: BigInteger, decimals: Int, symbol: String): String? =
        units.takeIf { it > BigInteger.ZERO }?.let { "${BigDecimal(it, decimals).stripTrailingZeros().toPlainString()} $symbol" }

    private suspend fun chain(slot: UniversalWalletSlot): Chain? =
        chainsRepository.getChains().firstOrNull { slot.matches(it.id) }

    private fun String.isPositiveAmount(): Boolean =
        toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } ?: (toBigIntegerOrNull()?.let { it > BigInteger.ZERO } ?: true)

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L

        // A full receive and change discovery reads one address per request.
        const val BITCOIN_TIMEOUT_MILLIS = 20_000L
        private const val MAX_IROHA_PAGES = 20
        private const val BITCOIN_DECIMALS = 8
        private const val TON_DECIMALS = 9
    }
}
