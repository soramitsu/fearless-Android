package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.Signer
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import java.math.BigInteger

class WalletConnectInteractorImplTest {

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"

    private val chainsRepository = mockk<ChainsRepository>()
    private val ethereumSource = mockk<EthereumRemoteSource>()
    private val accountRepository = mockk<AccountRepository>()
    private val keypairProvider = mockk<KeypairProvider>()
    private val extrinsicService = mockk<ExtrinsicService>()
    private val sessionClient = FakeSessionClient()
    private val transactionSender = FakeTransactionSender()
    private val signedRequests = WalletConnectSignedRequests()

    @Test
    fun `malformed personal message fails the request without reading keys`() {
        sessionClient.activeSession = session("eip155:1:$evmAddress")
        val request = sessionRequest(id = 11, method = "personal_sign", params = """["0xzz", "$evmAddress"]""", chainId = "eip155:1")

        val outcome = sign(ethereumChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        assertEquals(11L, (sessionClient.responses.single() as Wallet.Model.JsonRpcResponse.JsonRpcError).id)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `params that are not json fail the request instead of throwing`() {
        val request = sessionRequest(id = 12, method = "polkadot_signMessage", params = "not json", chainId = polkadotChain.caip2id)

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        assertTrue(sessionClient.responses.single() is Wallet.Model.JsonRpcResponse.JsonRpcError)
    }

    @Test
    fun `account the session did not approve is refused before any key is read`() {
        val approved = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val otherWallet = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(2))
        sessionClient.activeSession = session("${polkadotChain.caip2id}:${SS58Encoder.encode(approved.publicKey, 0)}")
        val request = polkadotSignMessage(id = 21, address = SS58Encoder.encode(otherWallet.publicKey, 0))

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        assertTrue(sessionClient.responses.single() is Wallet.Model.JsonRpcResponse.JsonRpcError)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `request is refused when the session is gone`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val request = polkadotSignMessage(id = 22, address = SS58Encoder.encode(keypair.publicKey, 0))

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `request for another network than the screen's is refused`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val address = SS58Encoder.encode(keypair.publicKey, 2)
        sessionClient.activeSession = session("polkadot:${KUSAMA_GENESIS.take(32)}:$address")
        val request = sessionRequest(
            id = 23,
            method = "polkadot_signMessage",
            params = """{"address": "$address", "message": "hello"}""",
            chainId = "polkadot:${KUSAMA_GENESIS.take(32)}"
        )

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `ethereum method on a polkadot network is refused`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val address = SS58Encoder.encode(keypair.publicKey, 0)
        sessionClient.activeSession = session("${polkadotChain.caip2id}:$address")
        val request = sessionRequest(
            id = 24,
            method = "personal_sign",
            params = """["0x68656c6c6f", "$address"]""",
            chainId = polkadotChain.caip2id
        )

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `message is signed with the key of exactly the approved account in any SS58 format`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        sessionClient.activeSession = session("${polkadotChain.caip2id}:${SS58Encoder.encode(keypair.publicKey, 0)}")
        coEvery { keypairProvider.getKeypairFor(polkadotChain, sameBytes(keypair.publicKey)) } returns keypair
        coEvery { keypairProvider.getCryptoTypeFor(polkadotChain, sameBytes(keypair.publicKey)) } returns CryptoType.ED25519
        val request = polkadotSignMessage(id = 25, address = SS58Encoder.encode(keypair.publicKey, 42), message = "hello")

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.succeeded)
        val signature = Json.parseToJsonElement(singleResult()).jsonObject["signature"]?.jsonPrimitive?.content.orEmpty().fromHex()
        assertTrue(Signer.verifyEd25519("<Bytes>hello</Bytes>".encodeToByteArray(), signature, keypair.publicKey))
    }

    @Test
    fun `typed data is signed with the approved account's key whatever the address case`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")
        coEvery { keypairProvider.getKeypairFor(ethereumChain, sameBytes(address.fromHex())) } returns keypair
        val request = typedData(id = 26, address = Keys.toChecksumAddress(address), chainId = "1")

        val outcome = sign(ethereumChain, request)

        assertTrue(outcome.succeeded)
        val result = singleResult().fromHex()
        val signatureData = Sign.SignatureData(result.copyOfRange(64, 65), result.copyOfRange(0, 32), result.copyOfRange(32, 64))
        val hash = StructuredDataEncoder(typedDataJson(chainId = "1")).hashStructuredData()
        assertEquals(Sign.publicKeyFromPrivate(BigInteger(1, keypair.privateKey)), Sign.signedMessageHashToKey(hash, signatureData))
    }

    @Test
    fun `polkadot transaction for another genesis hash is refused before any key is read`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val address = SS58Encoder.encode(keypair.publicKey, 0)
        sessionClient.activeSession = session("${polkadotChain.caip2id}:$address")

        val outcome = sign(polkadotChain, polkadotTransaction(id = 31, address = address, genesisHash = "0x$KUSAMA_GENESIS"))

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        assertTrue(sessionClient.responses.single() is Wallet.Model.JsonRpcResponse.JsonRpcError)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `polkadot transaction naming another account in its payload is refused`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val other = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(2))
        val address = SS58Encoder.encode(keypair.publicKey, 0)
        sessionClient.activeSession = session("${polkadotChain.caip2id}:$address")
        val request = polkadotTransaction(id = 32, address = address, payloadAddress = SS58Encoder.encode(other.publicKey, 0))

        val outcome = sign(polkadotChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `polkadot transaction for the request network and account is signed over its payload`() {
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed(1))
        val address = SS58Encoder.encode(keypair.publicKey, 0)
        sessionClient.activeSession = session("${polkadotChain.caip2id}:$address")
        coEvery { keypairProvider.getKeypairFor(polkadotChain, sameBytes(keypair.publicKey)) } returns keypair
        coEvery { keypairProvider.getCryptoTypeFor(polkadotChain, sameBytes(keypair.publicKey)) } returns CryptoType.ED25519
        val request = polkadotTransaction(
            id = 33,
            address = address,
            genesisHash = "0x${POLKADOT_GENESIS.uppercase()}",
            payloadAddress = SS58Encoder.encode(keypair.publicKey, 42)
        )

        val outcome = sign(polkadotChain, request, interactor(extrinsicService = ExtrinsicService(mockk(), keypairProvider, mockk())))

        assertTrue(outcome.succeeded)
        val result = Json.parseToJsonElement(singleResult()).jsonObject
        val signature = result["signature"]?.jsonPrimitive?.content.orEmpty().fromHex()
        assertEquals("33", result["id"]?.jsonPrimitive?.content)
        assertTrue(Signer.verifyEd25519(polkadotSigningPayload(), signature.copyOfRange(1, signature.size), keypair.publicKey))
    }

    @Test
    fun `transaction signing on an Ethereum based network is refused`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("${moonbeamChain.caip2id}:$address")
        val request = polkadotTransaction(id = 34, address = address, genesisHash = "0x$MOONBEAM_GENESIS", chainId = moonbeamChain.caip2id)

        val outcome = sign(moonbeamChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `typed data whose domain names another chain is refused before any key is read`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")

        val outcome = sign(ethereumChain, typedData(id = 35, address = address, chainId = "137"))

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        assertTrue(sessionClient.responses.single() is Wallet.Model.JsonRpcResponse.JsonRpcError)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `typed data domain chain id is compared whether written as a number, decimal or hex`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")
        coEvery { keypairProvider.getKeypairFor(ethereumChain, any()) } returns keypair

        assertTrue(sign(ethereumChain, typedData(id = 36, address = address, chainId = "\"1\"")).succeeded)
        assertTrue(sign(ethereumChain, typedData(id = 37, address = address, chainId = "\"0x89\"")).signError is WalletConnectRequestRejectedException)
    }

    @Test
    fun `ethereum transaction for another chain is refused before any key is read`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")

        val outcome = sign(ethereumChain, ethTransaction(id = 38, from = address, extra = """"chainId": "0x38","""))

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `ethereum transaction without a sender is refused`() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")
        val request = sessionRequest(
            id = 39,
            method = "eth_signTransaction",
            params = """[{"to": "0x1111111111111111111111111111111111111111", "value": "0x1"}]""",
            chainId = "eip155:1"
        )

        val outcome = sign(ethereumChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    @Test
    fun `send transaction retried after a failed response sends the same hash and never signs again`() {
        val address = approveEvmAccount()
        coEvery { ethereumSource.signRawTransaction("1", any(), any()) } returns Result.success("0xsigned")
        sessionClient.failNextResponses = 1
        val request = ethTransaction(id = 41, from = address)

        val first = sign(ethereumChain, request)
        val retry = sign(ethereumChain, request)

        assertTrue(first.requestError != null)
        assertTrue(retry.succeeded)
        assertEquals("0xhash1", retry.operationHash)
        assertEquals(listOf("0xhash1", "0xhash1"), results())
        assertEquals(listOf("0xsigned"), transactionSender.sent)
        coVerify(exactly = 1) { ethereumSource.signRawTransaction(any(), any(), any()) }
    }

    @Test
    fun `failed broadcast is retried with the same signed transaction instead of a new one`() {
        val address = approveEvmAccount()
        coEvery { ethereumSource.signRawTransaction("1", any(), any()) } returns Result.success("0xsigned")
        transactionSender.failNextSends = 1
        val request = ethTransaction(id = 42, from = address)

        val first = sign(ethereumChain, request)
        val retry = sign(ethereumChain, request)

        assertTrue(first.signError != null)
        assertTrue(retry.succeeded)
        assertEquals(listOf("0xsigned", "0xsigned"), transactionSender.sent)
        coVerify(exactly = 1) { ethereumSource.signRawTransaction(any(), any(), any()) }
    }

    @Test
    fun `retry from a new screen reuses what an earlier screen signed`() {
        val address = approveEvmAccount()
        coEvery { ethereumSource.signRawTransaction("1", any(), any()) } returns Result.success("0xsigned")
        sessionClient.failNextResponses = 1
        val request = ethTransaction(id = 43, from = address)

        sign(ethereumChain, request, interactor())
        val retry = sign(ethereumChain, request, interactor())

        assertTrue(retry.succeeded)
        assertEquals(listOf("0xsigned"), transactionSender.sent)
        coVerify(exactly = 1) { ethereumSource.signRawTransaction(any(), any(), any()) }
    }

    @Test
    fun `request that Verify reports as a scam is refused before any key is read`() {
        val address = approveEvmAccount()
        val request = typedData(id = 51, address = address, chainId = "1")
        sessionClient.verifyContexts[51] = Wallet.Model.VerifyContext(
            id = 51,
            origin = "https://drainer.example",
            validation = Wallet.Model.Validation.INVALID,
            verifyUrl = "",
            isScam = true
        )

        val outcome = sign(ethereumChain, request)

        assertTrue(outcome.signError is WalletConnectRequestRejectedException)
        coVerify(exactly = 0) { keypairProvider.getKeypairFor(any(), any()) }
    }

    private class Outcome {
        var signError: Exception? = null
        var operationHash: String? = null
        var succeeded = false
        var requestError: Wallet.Model.Error? = null
    }

    private fun interactor(extrinsicService: ExtrinsicService = this.extrinsicService) = WalletConnectInteractorImpl(
        chainsRepository = chainsRepository,
        ethereumSource = ethereumSource,
        accountRepository = accountRepository,
        keypairProvider = keypairProvider,
        extrinsicService = extrinsicService,
        transactionSender = transactionSender,
        sessionClient = sessionClient,
        signedRequests = signedRequests
    )

    private fun sign(
        chain: Chain,
        request: Wallet.Model.SessionRequest,
        interactor: WalletConnectInteractorImpl = interactor()
    ): Outcome = Outcome().also { outcome ->
        runBlocking {
            interactor.handleSignAction(
                chain = chain,
                topic = request.topic,
                recentSession = request,
                onSignError = { outcome.signError = it },
                onRequestSuccess = { hash, _ ->
                    outcome.succeeded = true
                    outcome.operationHash = hash
                },
                onRequestError = { outcome.requestError = it }
            )
        }
        if (outcome.signError == null && !outcome.succeeded && outcome.requestError == null) fail("no outcome was reported")
    }

    private fun seed(fill: Int) = ByteArray(32) { (it + fill).toByte() }

    private fun MockKMatcherScope.sameBytes(expected: ByteArray) = match<ByteArray> { it.contentEquals(expected) }

    private fun results(): List<String?> = sessionClient.responses.map { response ->
        (response as Wallet.Model.JsonRpcResponse.JsonRpcResult).result
    }

    private fun singleResult(): String =
        requireNotNull((sessionClient.responses.single() as Wallet.Model.JsonRpcResponse.JsonRpcResult).result)

    private fun polkadotSignMessage(
        id: Long,
        address: String,
        message: String = "hello"
    ) = sessionRequest(
        id = id,
        method = "polkadot_signMessage",
        params = buildJsonObject {
            put("address", address)
            put("message", message)
        }.toString(),
        chainId = polkadotChain.caip2id
    )

    private fun typedDataJson(chainId: String) =
        """{"types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},""" +
            """{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],""" +
            """"Person":[{"name":"name","type":"string"},{"name":"wallet","type":"address"}],""" +
            """"Mail":[{"name":"from","type":"Person"},{"name":"to","type":"Person"},{"name":"contents","type":"string"}]},""" +
            """"primaryType":"Mail","domain":{"name":"Ether Mail","version":"1","chainId":$chainId,""" +
            """"verifyingContract":"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"},""" +
            """"message":{"from":{"name":"Cow","wallet":"0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"},""" +
            """"to":{"name":"Bob","wallet":"0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"},"contents":"Hello, Bob!"}}"""

    private fun typedData(
        id: Long,
        address: String,
        chainId: String
    ) = sessionRequest(
        id = id,
        method = "eth_signTypedData_v4",
        params = buildJsonArray {
            add(address)
            add(typedDataJson(chainId))
        }.toString(),
        chainId = "eip155:1"
    )

    private fun polkadotTransaction(
        id: Long,
        address: String,
        genesisHash: String = "0x$POLKADOT_GENESIS",
        payloadAddress: String = address,
        chainId: String = polkadotChain.caip2id
    ) = sessionRequest(
        id = id,
        method = "polkadot_signTransaction",
        params = buildJsonObject {
            put("address", address)
            put(
                "transactionPayload",
                buildJsonObject {
                    put("address", payloadAddress)
                    put("blockHash", "0x$BLOCK_HASH")
                    put("era", "0xf500")
                    put("genesisHash", genesisHash)
                    put("method", "0x0503008eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a480700e40b5402")
                    put("nonce", "0x00000004")
                    put("specVersion", "0x00002468")
                    put("tip", "0x00000000000000000000000000000000")
                    put("transactionVersion", "0x0000000e")
                }
            )
        }.toString(),
        chainId = chainId
    )

    // method, era, compact nonce 4, compact tip 0, spec 9320, transaction version 14, genesis and block hash
    private fun polkadotSigningPayload() = (
        "0503008eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a480700e40b5402" + "f500" + "10" + "00" +
            "68240000" + "0e000000" + POLKADOT_GENESIS + BLOCK_HASH
        ).fromHex()

    private fun ethTransaction(
        id: Long,
        from: String,
        extra: String = ""
    ) = sessionRequest(
        id = id,
        method = "eth_sendTransaction",
        params = """[{$extra "from": "$from", "to": "0x1111111111111111111111111111111111111111", "value": "0x1"}]""",
        chainId = "eip155:1"
    )

    private fun approveEvmAccount(): String {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(seed(3))
        val address = Credentials.create(keypair.privateKey.toHexString()).address
        sessionClient.activeSession = session("eip155:1:$address")
        coEvery { keypairProvider.getKeypairFor(ethereumChain, sameBytes(address.fromHex())) } returns keypair
        return address
    }

    /** Records each broadcast and fails the next [failNextSends] of them like an unreachable node. */
    private class FakeTransactionSender : EvmTransactionSender {
        val sent = mutableListOf<String>()
        var failNextSends = 0

        override suspend fun send(chainId: String, signedTransaction: String): String {
            sent += signedTransaction
            if (failNextSends > 0) {
                failNextSends -= 1
                error("node unreachable")
            }
            return "0xhash${sent.size}"
        }
    }

    private companion object {
        const val BLOCK_HASH = "554d682a74099d05e8b7852d19c93b527b5fae1e9e1969f6e1b82a2f09a14cc9"
    }
}
