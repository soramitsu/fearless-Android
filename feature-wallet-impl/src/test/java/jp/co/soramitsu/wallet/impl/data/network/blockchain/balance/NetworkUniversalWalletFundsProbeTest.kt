package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import io.mockk.coEvery
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.UniversalWalletFundsProbe.Funds
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSource
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncResult
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.model.UniversalWalletEcosystem
import jp.co.soramitsu.common.model.UniversalWalletIndexedAssetBalance
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.BitcoinTransferServiceProviderTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkUniversalWalletFundsProbeTest {

    private val chains = mockk<ChainsRepository> {
        coEvery { getChains() } returns listOf(
            BitcoinTransferServiceProviderTest.bitcoinChain(),
            BitcoinTransferServiceProviderTest.tonChain(),
            BitcoinTransferServiceProviderTest.irohaChain()
        )
    }
    private val bitcoin = mockk<BitcoinIndexerClient>()
    private val solana = mockk<SolanaBalanceSource>()
    private val ton = mockk<TonRemoteSource>()
    private val iroha = mockk<IrohaToriiClient>()
    private val probe = NetworkUniversalWalletFundsProbe(chains, bitcoin, solana, ton, iroha, timeoutMillis = 20_000, bitcoinTimeoutMillis = 20_000)

    @Test
    fun `bitcoin is empty only when every discovered address holds nothing`() = runBlocking {
        val funded = UniversalWalletSlot.BITCOIN_MAINNET.address(UniversalWalletSlot.BITCOIN_MAINNET.publicKey(MNEMONIC, ""))
        coEvery { bitcoin.address(any(), any(), any()) } answers { stats(firstArg(), 0) }
        assertEquals(Funds.EMPTY, bitcoinProbe())

        coEvery { bitcoin.address(any(), any(), any()) } answers { stats(firstArg(), if (firstArg<String>() == funded) 5_000 else 0) }
        assertEquals(Funds.FUNDED, bitcoinProbe())

        coEvery { bitcoin.address(any(), any(), any()) } throws IllegalStateException("offline")
        assertEquals(Funds.UNKNOWN, bitcoinProbe())

        // A probe that does not answer in time keeps the old account.
        coEvery { bitcoin.address(any(), any(), any()) } coAnswers {
            delay(60_000)
            stats(firstArg(), 0)
        }
        val impatient = NetworkUniversalWalletFundsProbe(chains, bitcoin, solana, ton, iroha, timeoutMillis = 100, bitcoinTimeoutMillis = 100)
        assertEquals(
            Funds.UNKNOWN,
            impatient.probe(UniversalWalletSlot.BITCOIN_MAINNET, MNEMONIC, UniversalWalletSlot.BITCOIN_MAINNET.publicKey(MNEMONIC, ""))
        )
    }

    @Test
    fun `solana counts coin and every listed token and needs the token list to call it empty`() = runBlocking {
        val key = UniversalWalletSlot.SOLANA.publicKey(MNEMONIC, "")
        coEvery { solana.balances(any(), any(), any(), any(), any()) } returns solanaResult("0", emptyList(), tokensAvailable = true)
        assertEquals(Funds.EMPTY, probe.probe(UniversalWalletSlot.SOLANA, MNEMONIC, key))

        coEvery { solana.balances(any(), any(), any(), any(), any()) } returns solanaResult("1", emptyList(), tokensAvailable = true)
        assertEquals(Funds.FUNDED, probe.probe(UniversalWalletSlot.SOLANA, MNEMONIC, key))

        coEvery { solana.balances(any(), any(), any(), any(), any()) } returns solanaResult("0", listOf("250"), tokensAvailable = true)
        assertEquals(Funds.FUNDED, probe.probe(UniversalWalletSlot.SOLANA, MNEMONIC, key))

        coEvery { solana.balances(any(), any(), any(), any(), any()) } returns solanaResult("0", emptyList(), tokensAvailable = false)
        assertEquals(Funds.UNKNOWN, probe.probe(UniversalWalletSlot.SOLANA, MNEMONIC, key))
    }

    @Test
    fun `ton reads the coin and jettons live and a partial jetton list is unknown`() = runBlocking {
        val key = UniversalWalletSlot.TON.publicKey(MNEMONIC, "")
        coEvery { ton.loadAccountData(any(), any()) } returns tonAccount(0)
        coEvery { ton.loadJettonBalances(any(), any()) } returns JettonsBalances(emptyList())
        assertEquals(Funds.EMPTY, probe.probe(UniversalWalletSlot.TON, MNEMONIC, key))

        coEvery { ton.loadJettonBalances(any(), any()) } returns JettonsBalances(emptyList(), partial = true)
        assertEquals(Funds.UNKNOWN, probe.probe(UniversalWalletSlot.TON, MNEMONIC, key))

        coEvery { ton.loadAccountData(any(), any()) } returns tonAccount(7)
        assertEquals(Funds.FUNDED, probe.probe(UniversalWalletSlot.TON, MNEMONIC, key))
    }

    @Test
    fun `taira is empty only when every asset quantity is zero`() = runBlocking {
        val key = UniversalWalletSlot.TAIRA.publicKey(MNEMONIC, "")
        coEvery { iroha.accountAssets(any(), any(), any(), any(), any()) } returns IrohaAccountAssetListResponse(listOf(asset("0")))
        assertEquals(Funds.EMPTY, probe.probe(UniversalWalletSlot.TAIRA, MNEMONIC, key))

        coEvery { iroha.accountAssets(any(), any(), any(), any(), any()) } returns IrohaAccountAssetListResponse(listOf(asset("0"), asset("1.5")))
        assertEquals(Funds.FUNDED, probe.probe(UniversalWalletSlot.TAIRA, MNEMONIC, key))

        coEvery { iroha.accountAssets(any(), any(), any(), any(), any()) } throws IllegalStateException("404")
        assertEquals(Funds.UNKNOWN, probe.probe(UniversalWalletSlot.TAIRA, MNEMONIC, key))
    }

    private suspend fun bitcoinProbe() = probe.probe(
        UniversalWalletSlot.BITCOIN_MAINNET, MNEMONIC, UniversalWalletSlot.BITCOIN_MAINNET.publicKey(MNEMONIC, "")
    )

    private fun stats(address: String, funded: Long) = BitcoinEsploraAddress(
        address = address,
        chainStats = BitcoinEsploraStats(if (funded > 0) 1 else 0, funded, 0, 0, if (funded > 0) 1 else 0),
        mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
    )

    private fun solanaResult(lamports: String, tokens: List<String>, tokensAvailable: Boolean): SolanaBalanceSyncResult {
        val network = UniversalWalletRegistry.solanaMainnet
        fun balance(assetId: String, amount: String, native: Boolean) = UniversalWalletIndexedAssetBalance(
            accountId = network.id, ecosystem = UniversalWalletEcosystem.Solana, chainId = network.chainId,
            assetId = assetId, amount = amount, decimals = 9, isNative = native, symbol = "SOL", name = "Solana",
            uiAmountString = amount, syncedAtMillis = 1
        )
        val native = balance(network.nativeAsset.id, lamports, true)
        val tokenBalances = tokens.mapIndexed { index, amount -> balance("mint$index", amount, false) }
        return SolanaBalanceSyncResult("wallet", network.id, network.chainId, 1, native, tokenBalances, listOf(native) + tokenBalances,
            tokensAvailable = tokensAvailable)
    }

    private fun tonAccount(balance: Long) = TonAccountData(
        address = "account", balance = balance, lastActivity = 0, status = AccountStatus.uninit, getMethods = emptyList(), isWallet = true
    )

    private fun asset(quantity: String) = IrohaAccountAssetListItem(asset = "xor#sora", quantity = quantity)

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
