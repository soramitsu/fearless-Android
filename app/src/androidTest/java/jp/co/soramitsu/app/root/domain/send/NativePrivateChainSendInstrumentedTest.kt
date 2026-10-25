package jp.co.soramitsu.app.root.domain.send

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.bitcoin.*
import jp.co.soramitsu.common.data.network.solana.*
import jp.co.soramitsu.common.data.network.send.*
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.common.utils.SolanaKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.*
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real protocol/signing/storage against disposable private chains; public BIP39 vectors only. */
@RunWith(AndroidJUnit4::class)
class NativePrivateChainSendInstrumentedTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
            require(chain.request().url.host == "127.0.0.1") { "Private tests forbid any non-loopback request" }
            chain.proceed(chain.request())
        }.build()
    private fun retrofit() = jp.co.soramitsu.common.data.network.NetworkApiCreator(http, "http://127.0.0.1/")

    @Test fun bitcoinReviewedFeeOwnedChangeRestartRetryAndMaxMineWithoutDuplicatePayment() = runBlocking {
        assumeTrue(arguments.getString("bitcoinUrl") != null)
        val url = arguments.getString("bitcoinUrl")!!
        val chain = bitcoinChain(url)
        val root = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Testnet)
        val recipient = BitcoinKeyDerivation.deriveAccount(OTHER_MNEMONIC, network = BitcoinKeyDerivation.Network.Testnet).firstReceiveAddress
        val change = BitcoinKeyDerivation.deriveKey(MNEMONIC, derivationPath = "m/84'/1'/0'/1/0", network = BitcoinKeyDerivation.Network.Testnet).address
        control("/bitcoin/fund", """{"address":"${root.firstReceiveAddress}","sats":500000}""")
        val client = RetrofitBitcoinIndexerClient(retrofit().create(BitcoinIndexerApi::class.java))
        val preferences = preferences("bitcoin")
        val journal = WalletSendJournal(preferences)
        val quotes = NativeSendQuoteStore()
        var submissions = 0
        val signed = mutableListOf<String>()
        val ambiguous = object : BitcoinIndexerClient by client {
            override suspend fun broadcastTransaction(txHex: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): String {
                submissions++; signed += txHex
                val result = client.broadcastTransaction(txHex, network, baseUrl)
                if (submissions == 1) throw IOException("Simulated lost response after local acceptance")
                return result
            }
        }
        val accounts = accounts(chain, root.publicKey, false)
        fun service(store: NativeSendQuoteStore = quotes) = BitcoinTransferService(chain, accounts, ambiguous, store,
            NativeSendExecutor(WalletSendJournal(preferences)), SendPausePolicy.Unrestricted)
        val intent = Transfer(root.firstReceiveAddress, recipient, BigDecimal("0.0006"), chain.assets.single(), quoteId = UUID.randomUUID().toString())
        val fee = service().getTransferFee(intent)
        val hash = service().transfer(intent.copy(estimateFee = fee))
        val scope = "1:${chain.id.length}:${chain.id}"
        val retained = requireNotNull(journal.read(scope))
        assertEquals("60000", retained.reviewed?.amountUnits)
        assertEquals(recipient, retained.reviewed?.recipient)
        assertEquals(fee.movePointRight(8).toBigIntegerExact().toString(), retained.feeUnits)
        assertEquals(PendingWalletSend.State.Pending, retained.state)
        assertEquals(1, control("/bitcoin/mempool").getAsJsonArray("result").size())
        // New process has no cached quote. It can retry only the original quote ID and bytes.
        assertEquals(hash, service(NativeSendQuoteStore()).transfer(intent.copy(estimateFee = fee)))
        assertEquals(2, submissions); assertEquals(signed[0], signed[1])
        val differentMax = intent.copy(sendMax = true, amount = BigDecimal("999"), quoteId = UUID.randomUUID().toString(), estimateFee = BigDecimal.ZERO)
        val blocked = runCatching { service(NativeSendQuoteStore()).transfer(differentMax) }.exceptionOrNull()
        assertEquals(NativeSendException.Code.PAYMENT_PENDING, (blocked as NativeSendException).code)
        assertEquals(2, submissions)
        control("/bitcoin/mine", """{"blocks":1}""")
        val tx = control("/bitcoin/tx/$hash").getAsJsonObject("result")
        assertEquals(2, tx.getAsJsonArray("vout").size())
        assertEquals(0xfffffffdL, tx.getAsJsonArray("vin")[0].asJsonObject["sequence"].asLong)
        val outputs = tx.getAsJsonArray("vout").map { it.asJsonObject }
        assertTrue(outputs.any { it["testnetAddress"]?.asString == change })
        assertEquals(BigDecimal("0.00060000"), outputs.single { it["testnetAddress"]?.asString == recipient }["value"].asBigDecimal.setScale(8))
        assertEquals(BigDecimal("0.00500000").subtract(fee).setScale(8), outputs.fold(BigDecimal.ZERO) { acc, output -> acc + output["value"].asBigDecimal }.setScale(8))
        assertEquals(hash, service(NativeSendQuoteStore()).transfer(intent.copy(estimateFee = fee)))
        assertEquals(PendingWalletSend.State.Confirmed, journal.read(scope)?.state)
        assertEquals(2, submissions)
        // Max discovers and spends the owned change output, instead of looking only at receive index zero.
        val maxIntent = intent.copy(amount = BigDecimal.ONE, quoteId = UUID.randomUUID().toString(), sendMax = true)
        val maxAmount = service().getTransferMaxAmount(maxIntent)
        val maxFee = service().getTransferFee(maxIntent)
        val maxHash = service().transfer(maxIntent.copy(amount = maxAmount, estimateFee = maxFee))
        control("/bitcoin/mine", """{"blocks":1}""")
        val maxTx = control("/bitcoin/tx/$maxHash").getAsJsonObject("result")
        assertEquals(1, maxTx.getAsJsonArray("vout").size())
        assertEquals(maxAmount.setScale(8), maxTx.getAsJsonArray("vout")[0].asJsonObject["value"].asBigDecimal.setScale(8))
        assertTrue(maxTx.getAsJsonArray("vin").any { it.asJsonObject["txid"].asString == hash })
        assertTrue(client.utxos(change, BitcoinIndexerRoutes.Network.Testnet, url).isEmpty())
    }

    @Test fun solanaNativeAndSplCreationRentExistingAtaAndExactRetryUseOnlyLocalRpc() = runBlocking {
        assumeTrue(arguments.getString("solanaUrl") != null)
        val url = arguments.getString("solanaUrl")!!; val mint = arguments.getString("solanaMint")!!
        val chain = solanaChain(url, mint)
        val owner = SolanaKeyDerivation.deriveAccount(MNEMONIC)
        val recipient = SolanaKeyDerivation.deriveAccount(OTHER_MNEMONIC).address
        val genesisHash = requireNotNull(arguments.getString("solanaGenesisHash"))
        val network = UniversalWalletRegistry.solanaDevnet.copy(rpcUrl = url, genesisHash = genesisHash)
        val real = RetrofitSolanaRpcClient(retrofit().create(SolanaRpcApi::class.java), url, expectedGenesisHash = genesisHash)
        var statusUnknown = true; var submissions = 0; val signed = mutableListOf<String>()
        fun ambiguous(delegate: SolanaRpcClient): SolanaRpcClient = object : SolanaRpcClient by delegate {
            override fun withGenesisHash(expectedGenesisHash: String) = ambiguous(delegate.withGenesisHash(expectedGenesisHash))
            override fun withEndpoints(rpcUrls: List<String>) = ambiguous(delegate.withEndpoints(rpcUrls))
            override suspend fun signatureStatus(signature: String, rpcUrl: String?) = if (statusUnknown) null else delegate.signatureStatus(signature, rpcUrl)
            override suspend fun sendRawTransaction(transactionBase64: String, options: SolanaBroadcastOptions, rpcUrl: String?): String {
                submissions++; signed += transactionBase64
                val result = delegate.sendRawTransaction(transactionBase64, options, rpcUrl)
                if (submissions == 1) throw IOException("Simulated lost local response")
                return result
            }
        }
        val prefs = preferences("solana"); val accounts = accounts(chain, owner.publicKey, true); val quotes = NativeSendQuoteStore()
        fun service(store: NativeSendQuoteStore = quotes) = SolanaTransferService(chain, accounts, ambiguous(real), store,
            NativeSendExecutor(WalletSendJournal(prefs)), SendPausePolicy.Unrestricted, pollDelayMillis = 0, network = network)
        val native = Transfer(owner.address, recipient, BigDecimal("0.001"), chain.assets.first(), quoteId = UUID.randomUUID().toString())
        val starting = real.balance(owner.address)
        val fee = service().getTransferFee(native)
        val signature = service().transfer(native.copy(estimateFee = fee))
        assertEquals(signature, service(NativeSendQuoteStore()).transfer(native.copy(estimateFee = fee)))
        assertEquals(2, submissions); assertEquals(signed[0], signed[1])
        awaitConfirmed(real, signature); statusUnknown = false
        assertEquals(signature, service(NativeSendQuoteStore()).transfer(native.copy(estimateFee = fee)))
        assertEquals(2, submissions)
        assertEquals(1_000_000L, real.balance(recipient))
        assertEquals(starting - 1_000_000L - fee.movePointRight(9).longValueExact(), real.balance(owner.address))
        val journal = WalletSendJournal(prefs); val scope = "1:${chain.id.length}:${chain.id}"
        assertEquals(PendingWalletSend.State.Confirmed, journal.read(scope)?.state)
        val token = Transfer(owner.address, recipient, BigDecimal("1.25"), chain.assets.last(), quoteId = UUID.randomUUID().toString())
        val nativeBefore = real.balance(owner.address)
        val tokenFee = service().getTransferFee(token)
        val review = requireNotNull(quotes.reviews.value[token.quoteId])
        assertEquals(0, BigDecimal("0.002039280").compareTo(review.accountCreation))
        val tokenSignature = service().transfer(token.copy(estimateFee = tokenFee))
        awaitConfirmed(real, tokenSignature)
        assertEquals(tokenSignature, service().transfer(token.copy(estimateFee = tokenFee)))
        assertEquals(nativeBefore - tokenFee.movePointRight(9).longValueExact(), real.balance(owner.address))
        assertEquals(BigInteger("1250000"), real.tokenAccounts(recipient, TOKEN_PROGRAM).single { it.mint == mint }.amount)
        val repeated = token.copy(amount = BigDecimal("0.5"), quoteId = UUID.randomUUID().toString())
        val existingFee = service().getTransferFee(repeated)
        assertEquals(0, requireNotNull(quotes.reviews.value[repeated.quoteId]).accountCreation.compareTo(BigDecimal.ZERO))
        val second = service().transfer(repeated.copy(estimateFee = existingFee)); awaitConfirmed(real, second)
        assertEquals(second, service().transfer(repeated.copy(estimateFee = existingFee)))
        assertEquals(BigInteger("1750000"), real.tokenAccounts(recipient, TOKEN_PROGRAM).single { it.mint == mint }.amount)
        // Native Max is the actual balance less the reviewed network fee, including priority fee.
        val max = native.copy(amount = BigDecimal.ONE, sendMax = true, quoteId = UUID.randomUUID().toString())
        val amount = service().getTransferMaxAmount(max); val maxFee = service().getTransferFee(max)
        assertEquals(real.balance(owner.address), amount.add(maxFee).movePointRight(9).longValueExact())
        val final = service().transfer(max.copy(amount = amount, estimateFee = maxFee)); awaitConfirmed(real, final)
        assertEquals(0L, real.balance(owner.address))
    }

    private suspend fun awaitConfirmed(rpc: SolanaRpcClient, signature: String) {
        repeat(80) {
            val status = rpc.signatureStatus(signature)
            if (status?.isConfirmed == true) { assertNull(status.errorJson); return }
            delay(250)
        }
        fail("Local Solana transaction did not confirm")
    }

    private fun control(route: String, body: String? = null): JsonObject {
        val request = Request.Builder().url(arguments.getString("controlUrl")!! + route)
            .header("Authorization", "Bearer " + arguments.getString("controlToken"))
        if (body != null) request.post(body.toRequestBody("application/json".toMediaType()))
        return http.newCall(request.build()).execute().use { response ->
            require(response.isSuccessful) { "Local control request failed: ${response.code}" }
            JsonParser.parseString(response.body!!.string()).asJsonObject
        }
    }

    private fun preferences(kind: String): PreferencesImpl {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return PreferencesImpl(context.getSharedPreferences("wp9-private-$kind-${UUID.randomUUID()}", Context.MODE_PRIVATE))
    }

    private fun accounts(chain: Chain, publicKey: ByteArray, solana: Boolean): AccountRepository {
        val wallet = MetaAccount(1, mapOf(chain.id to MetaAccount.ChainAccount(1, chain, publicKey, publicKey,
            if (solana) CryptoType.ED25519 else CryptoType.ECDSA, "Public local test")), emptyMap(), ByteArray(32), CryptoType.SR25519,
            ByteArray(32), null, null, null, true, true, null, "Public local test", true)
        val secrets = SubstrateSecrets(BaseKeypair(ByteArray(32), ByteArray(32)), MnemonicCreator.fromWords(MNEMONIC).entropy)
        return AccountRepository::class.java.cast(Proxy.newProxyInstance(AccountRepository::class.java.classLoader,
            arrayOf(AccountRepository::class.java)) { _, method, _ -> when (method.name) {
                "getSelectedMetaAccount" -> wallet
                "getChainAccountSecrets", "getEthereumSecrets", "getTonSecrets" -> null
                "getSubstrateSecrets" -> secrets
                else -> error("Unexpected account call: ${method.name}")
            } })!!
    }

    private fun bitcoinChain(url: String): Chain {
        val network = UniversalWalletRegistry.bitcoinTestnet
        return chain(network.id, network.name, listOf(asset(network.id, "BTC", "BTC", 8)), url, false)
    }
    private fun solanaChain(url: String, mint: String): Chain {
        val network = UniversalWalletRegistry.solanaDevnet
        return chain(network.id, network.name, listOf(asset(network.id, "SOL", "SOL", 9), asset(network.id, mint, "LOCAL", 6, mint)), url, true)
    }
    private fun asset(chainId: String, id: String, symbol: String, precision: Int, mint: String? = null) = Asset(id, symbol, symbol, "", chainId,
        "Private local chain", null, true, null, precision, Asset.StakingType.UNSUPPORTED, null, false, mint == null,
        ChainAssetType.Normal, mint, null, null, isNative = mint == null)
    private fun chain(id: String, name: String, assets: List<Asset>, url: String, solana: Boolean) = Chain(id, null, null, name, null, assets,
        if (solana) listOf(ChainNode(url, "Owned local validator", true, true)) else emptyList(), emptyList(),
        Chain.ExternalApi(null, Chain.ExternalApi.Section(if (solana) Chain.ExternalApi.Section.Type.SOLANA else Chain.ExternalApi.Section.Type.BITCOIN, url), null),
        "", 0, false, true, false, null, false, false, false, false, false, null, Ecosystem.Substrate, remoteAssetsSource = null)

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val OTHER_MNEMONIC = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    }
}
