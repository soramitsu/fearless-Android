package jp.co.soramitsu.tonconnect.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.DappRemote
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.network.ton.TonAppManifest
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.fearless_utils.encrypt.ton.TonEd25519
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.SecretBox
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeEvent
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import jp.co.soramitsu.tonconnect.impl.data.TonConnectBridgeEventLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.ton.boc.BagOfCells
import java.util.Base64
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair

@RunWith(RobolectricTestRunner::class)
class TonConnectInteractorImplTest {
    private val seed = ByteArray(32) { it.toByte() }
    private val publicKey = TonEd25519.publicKey(seed)
    private val wallet = mockk<MetaAccount> {
        every { id } returns 7L
        every { tonPublicKey } returns publicKey
    }
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns wallet
        coEvery { getMetaAccount(7) } returns wallet
        coEvery { allMetaAccounts() } returns listOf(wallet)
        every { selectedMetaAccountFlow() } returns flowOf(wallet)
    }
    private val chain = mockk<Chain> {
        every { id } returns tonMainnetChainId
        every { name } returns "TON"
        every { isTestNet } returns false
        every { tonBridgeUrl } returns "https://bridge.fixture.invalid"
    }
    private val chains = mockk<ChainsRepository> {
        coEvery { getChain(tonMainnetChainId) } returns chain
    }
    private val keypairs = mockk<KeypairProvider> {
        coEvery { getKeypairFor(chain, publicKey) } returns Keypair(publicKey, seed)
    }
    private val api = mockk<TonApi>()
    private val repository = mockk<TonConnectRepository>(relaxed = true)
    private val remote = mockk<TonRemoteSource>(relaxed = true)
    private val posted = mutableListOf<String>()
    private val client = OkHttpClient.Builder().addInterceptor { call ->
        val buffer = Buffer()
        call.request().body!!.writeTo(buffer)
        posted += buffer.readUtf8()
        Response.Builder().request(call.request()).code(200).message("OK").protocol(Protocol.HTTP_1_1)
            .body("ok".toResponseBody()).build()
    }.build()
    private val interactor = TonConnectInteractorImpl(
        chains, accounts, keypairs, api, repository, remote, keypairs, client, client,
        TonConnectBridgeEventLedger(mockk(relaxed = true)), currentTimeMillis = { 1_000_000L }
    )
    private val app = AppEntity("https://dapp.example", "Bird Swap", "", null, null)

    private val transactionRequest =
        """{"valid_until":1200,"network":"-239","messages":[{"address":"0:0101010101010101010101010101010101010101010101010101010101010101","amount":"1"}]}"""

    @Test
    fun chainAndDiscoveryConfigComeFromTheirRepositories(): Unit = runBlocking {
        val dapp = DappRemote("swap", listOf("-239"), "Bird Swap", app.url, "Trade", null, null)
        coEvery { api.getDappsConfig() } returns listOf(DappConfigRemote("DeFi", listOf(dapp)))
        assertSame(chain, interactor.getChain())
        val config = interactor.getDappsConfig().single()
        assertEquals("DeFi", config.type)
        assertEquals("swap", config.apps.single().identifier)
        assertEquals("Bird Swap", config.apps.single().name)
        assertNull(config.apps.single().metaId)
    }

    @Test
    fun manifestRetainsSameOriginUrlAndReplacesForeignOrInvalidUrls(): Unit = runBlocking {
        val manifest = "https://dapp.example/manifest.json"
        for ((claimed, expected) in listOf(
            "https://dapp.example/swap" to "https://dapp.example/swap",
            "https://evil.example/swap" to "https://dapp.example",
            "javascript:alert(1)" to "https://dapp.example"
        )) {
            coEvery { api.getManifest(manifest) } returns TonAppManifest(claimed, "Bird Swap", "icon", "terms", "privacy")
            val result = interactor.readManifest(manifest)
            assertEquals(expected, result.url)
            assertEquals("Bird Swap", result.name)
            assertEquals("icon", result.iconUrl)
            assertEquals("terms", result.termsOfUseUrl)
            assertEquals("privacy", result.privacyPolicyUrl)
        }
    }

    @Test
    fun connectedDappsAreScopedToSelectedWalletAndSource(): Unit = runBlocking {
        val local = connection("client", ConnectionSource.WEB)
        val connection = TonDappConnection(local)
        coEvery { repository.getConnections(7, ConnectionSource.WEB) } returns listOf(connection)
        every { repository.observeConnections(7, ConnectionSource.WEB) } returns flowOf(listOf(connection))
        val loaded = interactor.getConnectedDapps(ConnectionSource.WEB)
        val observed = interactor.getConnectedDappsFlow(ConnectionSource.WEB).first()
        assertEquals(loaded, observed)
        assertEquals(7L, loaded.apps.single().metaId)
        assertEquals(ConnectionSource.WEB, loaded.apps.single().connectionSource)
        assertEquals("client", loaded.apps.single().identifier)
    }

    @Test
    fun lookupRequiresUniqueOriginAndDisconnectUsesIdentity(): Unit = runBlocking {
        val local = connection("first", ConnectionSource.WEB)
        val connected = TonDappConnection(local)
        coEvery { repository.getConnections(7, ConnectionSource.WEB) } returns listOf(connected)
        coEvery { repository.getConnection(connected.identity()) } returns local
        assertSame(local, interactor.getConnection("https://dapp.example/another-path", ConnectionSource.WEB))
        assertSame(local, interactor.getConnection(connected.identity()))
        interactor.disconnect(connected.identity())
        coVerify { repository.deleteConnection(connected.identity()) }
        coEvery { repository.getConnections(7, ConnectionSource.WEB) } returns listOf(connected, connected)
        assertNull(interactor.getConnection(app.url, ConnectionSource.WEB))
        assertNull(interactor.getConnection("file:///tmp/app", ConnectionSource.WEB))
    }

    @Test
    fun browserConnectReplyIsStoredUnderTheWalletNamedByItsPublicKey(): Unit = runBlocking {
        val saved = slot<TonConnectionLocal>()
        val keys = slot<SigningKeypair>()
        coEvery { repository.saveConnection(capture(saved), capture(keys)) } returns Unit
        val result = interactor.respondDappConnectRequest(null, request(), JsonBuilder.connectEventSuccess(publicKey, null, null), app)
        assertNotNull(result)
        assertEquals(7L, saved.captured.metaId)
        assertEquals(ConnectionSource.WEB, saved.captured.source)
        assertEquals(app.url, saved.captured.url)
        assertEquals(32, saved.captured.clientId.length)
        assertEquals(32, keys.captured.privateKey.size)
        assertArrayEquals(Keys.generatePublicKey(keys.captured.privateKey), keys.captured.publicKey)
    }

    @Test
    fun rejectedBrowserConnectionIsNotStored(): Unit = runBlocking {
        assertNull(interactor.respondDappConnectRequest(null, request(), JsonBuilder.connectEventError(BridgeError.BAD_REQUEST), app))
        coVerify(exactly = 0) { repository.saveConnection(any(), any()) }
    }

    @Test
    fun proofUsesBoundWalletAndLeavesStoredSeedIntact(): Unit = runBlocking {
        val expectedSeed = seed.copyOf()
        val proof = interactor.requestProof(7, app, "challenge")
        assertEquals("dapp.example", proof.domain.value)
        assertEquals("challenge", proof.payload)
        assertEquals(64, Base64.getDecoder().decode(proof.signature).size)
        assertArrayEquals(expectedSeed, seed)
        coVerify { keypairs.getKeypairFor(chain, publicKey) }
    }

    @Test
    fun signPreparationValidatesRequestCountExpiryAndWallet(): Unit = runBlocking {
        val valid = transactionRequest
        val prepared = interactor.prepareSignRequest(listOf(valid), 7)
        assertEquals(1200L, prepared.validUntil)
        assertEquals(1, prepared.messages.size)
        for (params in listOf(emptyList(), listOf(valid, valid), listOf("bad-json"), listOf(valid.replace("1200", "900")))) {
            val failure = runCatching { interactor.prepareSignRequest(params, 7) }.exceptionOrNull()
            assertTrue(failure is BridgeError.Exception)
            assertEquals(BridgeError.BAD_REQUEST, (failure as BridgeError.Exception).error)
        }
    }

    @Test
    fun signingProducesValidCellsForNewAndInitializedWallets(): Unit = runBlocking {
        val request = interactor.prepareSignRequest(listOf(transactionRequest), 7)
        coEvery { remote.loadAccountData(chain, any()) } returns
            TonAccountData("recipient", 0, 0, AccountStatus.active, emptyList(), true)
        val results = mutableListOf<String>()
        for (seqno in listOf(0, 1)) {
            coEvery { remote.getSeqno(chain, any()) } returns seqno
            val (boc, hash) = interactor.signMessage(chain, "sendTransaction", request, 7)
            val root = BagOfCells(Base64.getDecoder().decode(boc)).roots.single()
            assertArrayEquals(root.hash().toByteArray(), java.util.HexFormat.of().parseHex(hash))
            results += boc
        }
        assertNotEquals(results[0], results[1])
        coVerify(exactly = 2) { keypairs.getKeypairFor(chain, publicKey) }
    }

    @Test
    fun blockchainSubmissionForwardsExactBoc(): Unit = runBlocking {
        interactor.sendBlockchainMessage(chain, "signed-boc")
        coVerify { remote.sendBlockchainMessage(chain, SendBlockchainMessageRequest("signed-boc")) }
    }

    @Test
    fun bridgeRepliesAreDecryptableAndUseRequestIdInsteadOfEventId(): Unit = runBlocking {
        val remotePrivate = Keys.generatePrivateKey()
        val remotePublic = Keys.generatePublicKey(remotePrivate)
        val localPrivate = Keys.generatePrivateKey()
        val localPublic = Keys.generatePublicKey(localPrivate)
        val connection = TonDappConnection(connection(remotePublic.toHexString(false), ConnectionSource.QR))
        coEvery { repository.getConnectionKeypair(connection.identity()) } returns Keypair(localPublic, localPrivate)
        val message = BridgeEvent.Message(JSONObject().put("method", "sendTransaction").put("id", 123).put("params", JSONArray()))
        val event = BridgeEvent(999, message, connection)
        interactor.sendDappMessage(event, "signed-boc")
        interactor.respondDappError(event, BridgeError.BAD_REQUEST)
        val decoded = posted.map { encoded ->
            val bytes = Base64.getDecoder().decode(encoded)
            val plaintext = SecretBox(localPublic, remotePrivate).open(bytes.copyOfRange(0, 24), bytes.copyOfRange(24, bytes.size))
            JSONObject(plaintext.toString(Charsets.UTF_8))
        }
        assertEquals(2, decoded.size)
        assertEquals(123L, decoded[0].getLong("id"))
        assertEquals("signed-boc", decoded[0].getString("result"))
        assertEquals(123L, decoded[1].getLong("id"))
        assertEquals(BridgeError.BAD_REQUEST.code, decoded[1].getJSONObject("error").getInt("code"))
    }

    private fun request() = ConnectRequest("https://dapp.example/manifest.json", listOf(ConnectRequest.Item.TonAddress))

    private fun connection(clientId: String, source: ConnectionSource) =
        TonConnectionLocal(7, clientId, "Bird Swap", "", app.url, source)
}
