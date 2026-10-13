package jp.co.soramitsu.account.impl.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.account.impl.domain.LegacyNetworkAccountUpgrade
import jp.co.soramitsu.account.impl.domain.account.details.offlineNexusAccount
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.model.UniversalWalletDerivationPaths
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.SolanaSigner
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ton.api.pk.PrivateKeyEd25519
import java.lang.reflect.Proxy

/**
 * Uses production import, native SR25519, Room, secret validation and account
 * provisioning. Only encrypted-preference IO and the repository facade are
 * test doubles; no public key or address is supplied to the restore path.
 */
@RunWith(AndroidJUnit4::class)
class UniversalWalletMnemonicRestoreDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val chains = listOf(
        chain("polkadot"),
        chain("ethereum", Ecosystem.Ethereum),
        chain("-239", Ecosystem.Ton),
        chain(UniversalWalletRegistry.bitcoinTestnet.id).copy(isTestNet = true),
        chain(UniversalWalletRegistry.bitcoinMainnet.id),
        chain(UniversalWalletRegistry.solanaMainnet.id),
        chain(UniversalWalletRegistry.taira.id).copy(isTestNet = true)
    ).associateBy(Chain::id)

    @Test
    fun twelveWordsRestoreSharedAddressesFromEmptyStorage() = runBlocking {
        assertFreshRestores("import12")
    }

    @Test
    fun twentyFourWordsRestoreSharedAddressesFromEmptyStorage() = runBlocking {
        assertFreshRestores("default24")
    }

    private suspend fun assertFreshRestores(vectorId: String) {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val vector = assets.open("universal-wallet-v2-vectors.json").bufferedReader().use { reader ->
            JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("vectors")
                .single { it.asJsonObject["id"].asString == vectorId }.asJsonObject
        }
        // Separate storage stacks prove phrase-only recovery, independent of
        // wallet IDs, names, previous public accounts or cached addresses.
        val first = restore(vector, "Original $vectorId")
        val replacement = restore(vector, "Replacement $vectorId")
        assertEquals(first, replacement)
    }

    private suspend fun restore(vector: JsonObject, name: String): Map<String, String> {
        val preferences = HashMapEncryptedPreferences()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.metaAccountDao()
            database.chainDao().update(emptyList(), chains.values.map(::mapChainToChainLocal))
            assertTrue(dao.getMetaAccounts().isEmpty())
            val coordinator = WalletSecretMutationCoordinator(
                database, dao, WalletSecretMutationJournalStore(preferences), preferences
            )
            val id = SubstrateOrEvmAccountRepository(dao, coordinator).create(
                AddAccountPayload.SubstrateOrEvm(
                    accountName = name,
                    mnemonic = vector["mnemonic"].asString,
                    encryptionType = CryptoType.SR25519,
                    substrateDerivationPath = UniversalWalletDerivationPaths.SUBSTRATE_ROOT,
                    ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                    googleBackupAddress = null,
                    isBackedUp = true
                )
            )
            val root = requireNotNull(dao.getMetaAccount(id))
            val substrate = requireNotNull(
                SubstrateSecretStore(preferences).get(
                    id, root.substratePublicKey, root.substrateCryptoType, root.substrateAccountId
                )
            )
            val ethereum = requireNotNull(
                EthereumSecretStore(preferences).get(id, root.ethereumPublicKey, root.ethereumAddress)
            )
            val entropy = MnemonicCreator.fromWords(vector["mnemonic"].asString).entropy
            assertArrayEquals(entropy, substrate[SubstrateSecrets.Entropy])
            assertArrayEquals(entropy, ethereum[EthereumSecrets.Entropy])
            assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

            val repository = Proxy.newProxyInstance(
                AccountRepository::class.java.classLoader, arrayOf(AccountRepository::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "allMetaAccounts" -> dao.getJoinedMetaAccountsInfo()
                        .map { mapMetaAccountLocalToMetaAccount(chains, it) }
                    "getMetaAccount" -> readWallet(dao, args[0] as Long)
                    "getSubstrateSecrets" -> SubstrateSecretStore(preferences).get(
                        id, root.substratePublicKey, root.substrateCryptoType, root.substrateAccountId
                    )
                    else -> error("Unexpected restore repository call: ${method.name}")
                }
            } as AccountRepository
            val upgrade = LegacyNetworkAccountUpgrade(repository, dao, coordinator) { chains.keys }
            upgrade.upgrade()
            val restored = readWallet(dao, id)
            val tonSecrets = requireNotNull(TonSecretStore(preferences).get(id, restored.tonPublicKey))
            assertTrue(
                TonKeyDerivation.matchesUniversalMnemonicKeypair(
                    vector["mnemonic"].asString, tonSecrets[TonSecrets.PrivateKey], tonSecrets[TonSecrets.PublicKey]
                )
            )
            val signingKey = KeyPairRepository(
                SecretStoreV2(preferences), EthereumSecretStore(preferences), SubstrateSecretStore(preferences),
                TonSecretStore(preferences), repository, WalletSecretAccessGuard(preferences)
            ).getKeypairFor(chains.getValue("-239"), requireNotNull(restored.tonPublicKey))
            assertArrayEquals(restored.tonPublicKey, signingKey.publicKey)
            val challenge = "offline restored TON signing regression".encodeToByteArray()
            val signer = PrivateKeyEd25519(signingKey.privateKey)
            assertTrue(SolanaSigner.verifyMessage(signingKey.publicKey, challenge, signer.sign(challenge)))
            val addresses = assertAddresses(restored, vector.getAsJsonObject("expected"))
            upgrade.upgrade()
            assertEquals(addresses, assertAddresses(readWallet(dao, id), vector.getAsJsonObject("expected")))
            return addresses
        } finally {
            database.close()
        }
    }

    private fun readWallet(dao: MetaAccountDao, id: Long): MetaAccount =
        mapMetaAccountLocalToMetaAccount(chains, dao.getJoinedMetaAccountsInfo().single { it.metaAccount.id == id })

    private fun assertAddresses(wallet: MetaAccount, expected: JsonObject): Map<String, String> {
        val bitcoinId = UniversalWalletRegistry.bitcoinMainnet.id
        val solanaId = UniversalWalletRegistry.solanaMainnet.id
        val taira = UniversalWalletRegistry.taira
        val nexus = UniversalWalletRegistry.nexus
        val substrate = expected.getAsJsonObject("substrate")
        val iroha = expected.getAsJsonObject("iroha")
        assertEquals(setOf(bitcoinId, UniversalWalletRegistry.bitcoinTestnet.id, solanaId, taira.id), wallet.chainAccounts.keys)
        assertEquals(substrate["publicKeyHex"].asString, wallet.substratePublicKey?.toHexString(false))
        val addresses = chains.mapValues { (_, chain) -> requireNotNull(wallet.address(chain)) }.toMutableMap()
        assertEquals(substrate["polkadotAddress"].asString, addresses["polkadot"])
        // Android presents lowercase EVM hex; the fixture uses EIP-55 casing.
        assertEquals(expected.getAsJsonObject("evm")["address"].asString.lowercase(), addresses["ethereum"])
        assertEquals(
            expected.getAsJsonObject("bitcoin").getAsJsonObject("mainnet")["firstReceiveAddress"].asString,
            addresses[bitcoinId]
        )
        assertEquals(expected.getAsJsonObject("solana")["address"].asString, addresses[solanaId])
        assertEquals(iroha.getAsJsonObject("taira")["i105"].asString, addresses[taira.id])
        val tairaKey = wallet.chainAccounts.getValue(taira.id).publicKey
        assertEquals(iroha.getAsJsonObject("taira")["publicKeyHex"].asString, tairaKey.toHexString(false))

        val offline = requireNotNull(offlineNexusAccount(wallet))
        val projection = requireNotNull(offline.projection)
        assertEquals(iroha.getAsJsonObject("nexus")["i105"].asString, projection.address)
        assertArrayEquals(tairaKey, projection.accountId)
        assertEquals(
            iroha.getAsJsonObject("nexus")["canonicalHex"].asString,
            IrohaAddressCodec.parse(projection.address, nexus.chainDiscriminant).canonicalHex
        )
        assertNotEquals(addresses[taira.id], projection.address)
        assertTrue(offline.isOffline)
        assertTrue(offline.chain.nodes.isEmpty())
        assertTrue(offline.chain.assets.isEmpty())
        assertTrue(taira.enabledByDefault)
        assertFalse(nexus.enabledByDefault)
        assertFalse(chains.containsKey(nexus.id))
        addresses[nexus.id] = projection.address

        assertEquals(expected.getAsJsonObject("ton")["addressNonBounceable"].asString, addresses["-239"])
        assertEquals(
            expected.getAsJsonObject("bitcoin").getAsJsonObject("testnet")["firstReceiveAddress"].asString,
            addresses[UniversalWalletRegistry.bitcoinTestnet.id]
        )
        return addresses
    }

    private fun chain(id: String, ecosystem: Ecosystem = Ecosystem.Substrate): Chain = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = id,
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = ecosystem == Ecosystem.Ethereum,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = ecosystem == Ecosystem.Ethereum,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = ecosystem,
        remoteAssetsSource = null
    )
}
