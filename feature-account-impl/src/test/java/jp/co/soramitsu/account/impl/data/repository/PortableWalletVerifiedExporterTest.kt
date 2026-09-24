package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.WalletCustodyDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/** Real wallet-secret schemas behind mocked Room/preferences; no fixture-only semantic input. */
class PortableWalletVerifiedExporterTest {
    private val role = PortableWalletSemanticMaterial.Role
    private val metaDao = mock<MetaAccountDao>()
    private val accounts = mock<AccountRepository>()
    private val preferences = mock<EncryptedPreferences>()
    private val custodyDao = mock<WalletCustodyDao>()
    private val journal = mock<WalletSecretMutationJournalStore>()
    private val userPreferences = mock<Preferences>()
    private val assetDao = mock<AssetDao>()
    private val exporter = PortableWalletMaterialPreflight(
        metaDao, accounts, preferences, custodyDao, journal, userPreferences, assetDao
    )
    private val genesis = "0x" + "01".repeat(32)
    private val policy = listOf(
        PortableWalletChainSigningProof.ApprovedGenesis(
            genesis, PortableWalletChainSigningProof.IdentityKind.SUBSTRATE
        )
    )

    @Test
    fun `exports mixed source cohort with independent buffers`(): Unit = runBlocking {
        val fixture = fixture()
        install(fixture)
        val first = exporter.captureVerifiedSemanticPlaintext(policy)
        val second = exporter.captureVerifiedSemanticPlaintext(policy)
        try {
            assertNotSame(first, second)
            assertArrayEquals(first, second)
            val decoded = PortableWalletSemanticMaterial.decode(first)
            try {
                assertEquals(4, decoded.wallets.size)
                assertEquals(0, decoded.selectedIndex)
                assertEquals(
                    listOf("Legacy", "Watch", "Standalone EVM", "Native TON"),
                    decoded.wallets.map { it.name }
                )
                assertEquals(listOf(0L, 1L, 2L, 3L), decoded.wallets.map { it.sourcePosition })
                assertEquals(1, decoded.countRole(role.LEGACY_SUBSTRATE))
                assertEquals(1, decoded.countRole(role.CHAIN_ACCOUNT))
                assertEquals(1, decoded.countRole(role.EVM_ROOT))
                assertEquals(1, decoded.countRole(role.TON_ROOT))
                assertEquals(1, decoded.countRole(role.FAVORITE_CHAIN))
                assertTrue(decoded.wallets[1].slots.all { it.role == role.WATCH_IDENTITY })
                assertFalse(decoded.wallets[2].slots.any { it.role == role.SUBSTRATE_ROOT })
            } finally {
                decoded.clearSecrets()
            }
            first.fill(0)
            assertFalse(second.all { it == 0.toByte() })
        } finally {
            first.fill(0)
            second.fill(0)
            fixture.clear()
        }
        verify(custodyDao, never()).insert(any())
    }

    @Test
    fun `rejects an unapproved chain or orphaned original namespace without promoting custody`() = runBlocking {
        val fixture = fixture()
        install(fixture)
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(emptyList()) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    exporter.captureVerifiedSemanticPlaintext(
                        listOf(
                            PortableWalletChainSigningProof.ApprovedGenesis(
                                genesis, PortableWalletChainSigningProof.IdentityKind.ETHEREUM
                            )
                        )
                    )
                }
            }
            fixture.namespace += "3:ACCESS_SECRETS" // Historical V2 root would otherwise be lost.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
            }
            verify(custodyDao, never()).insert(any())
        } finally {
            fixture.clear()
        }
    }

    @Test
    fun `rejects orphaned V2 and V3 namespaces outside Room without rejecting ordinary preferences`() = runBlocking {
        val fixture = fixture()
        install(fixture)
        try {
            fixture.namespace += setOf("99:display_order", "2026:sync_height", "99:api_secret_token")
            exporter.captureVerifiedSemanticPlaintext(policy).fill(0)
            listOf(
                "99:ACCESS_SECRETS",
                "99:SUBSTRATE_SECRETS",
                "99:ETHEREUM_SECRETS",
                "99:TON_SECRETS",
                "99:${"ab".repeat(32)}:ACCESS_SECRETS",
                "099:ETHEREUM_SECRETS"
            ).forEach { orphan ->
                fixture.namespace += orphan
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
                }
                fixture.namespace -= orphan
            }
            verify(custodyDao, never()).insert(any())
        } finally {
            fixture.clear()
        }
    }

    @Test
    fun `rejects globally orphaned quarantine and recovery aliases`() = runBlocking {
        val fixture = fixture()
        install(fixture)
        try {
            listOf(
                "security_source_${ByteArray(32) { 51 }.toAddress(42)}",
                "private_orphan",
                "wallet_secret_quarantine:99:ETHEREUM_SECRETS",
                "wallet_secret_quarantine:legacy_v1_public_deadbeef",
                "wallet_secret_quarantine:legacy_v04_meta_99_public_deadbeef",
                "wallet_secret_quarantine:security_source_orphan",
                "wallet_public_identity_recovery:99:deadbeef",
                "wallet_public_identity_recovery:3:deadbeef"
            ).forEach { orphan ->
                fixture.namespace += orphan
                assertThrows(IllegalStateException::class.java) {
                    runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
                }
                fixture.namespace -= orphan
            }
            verify(custodyDao, never()).insert(any())
        } finally {
            fixture.clear()
        }
    }

    @Test
    fun `rejects a namespace changed after signing proof without returning plaintext`() = runBlocking {
        val fixture = fixture()
        install(fixture)
        var reads = 0
        whenever(preferences.keysWithPrefixes(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val prefixes = invocation.getArgument<Set<String>>(0)
            if ("security_source_" in prefixes) {
                setOf("security_source_${fixture.v1Address}")
            } else if ("0" in prefixes) {
                reads++
                if (reads == 1) fixture.namespace.toSet() else fixture.namespace + "99:ETHEREUM_SECRETS"
            } else {
                emptySet<String>()
            }
        }
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
            }
            assertTrue(reads >= 2)
            verify(custodyDao, never()).insert(any())
        } finally {
            fixture.clear()
        }
    }

    @Test
    fun `rejects unmapped wallet preferences and historical private-key aliases`() = runBlocking {
        val fixture = fixture()
        install(fixture)
        try {
            whenever(userPreferences.contains("wallet_selected_chain_id3")).thenReturn(true)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
            }
            whenever(userPreferences.contains("wallet_selected_chain_id3")).thenReturn(false)
            whenever(assetDao.hasUnmappedWalletAssetPreferences(3)).thenReturn(true)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
            }
            whenever(assetDao.hasUnmappedWalletAssetPreferences(3)).thenReturn(false)
            whenever(preferences.keysWithPrefixes(any(), any(), any(), any(), any())).thenAnswer { invocation ->
                val prefixes = invocation.getArgument<Set<String>>(0)
                when {
                    "security_source_" in prefixes -> setOf("security_source_${fixture.v1Address}")
                    "private_" in prefixes -> setOf("private_old_key")
                    "0" in prefixes -> fixture.namespace.toSet()
                    else -> emptySet<String>()
                }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { exporter.captureVerifiedSemanticPlaintext(policy) }
            }
            verify(custodyDao, never()).insert(any())
        } finally {
            fixture.clear()
        }
    }

    private fun install(fixture: Fixture) {
        whenever(metaDao.getJoinedMetaAccountsInfo()).thenReturn(fixture.rows)
        runBlocking { whenever(accounts.isWalletRecoveryRequired(any())).thenReturn(false) }
        runBlocking { whenever(assetDao.hasUnmappedWalletAssetPreferences(any())).thenReturn(false) }
        runBlocking { whenever(accounts.getSecuritySource(fixture.v1Address)).thenReturn(fixture.v1Source) }
        runBlocking { whenever(accounts.getEthereumSecrets(3)).thenReturn(fixture.evmSecret) }
        runBlocking { whenever(accounts.getChainAccountSecrets(3, genesis)).thenReturn(fixture.chainSecret) }
        runBlocking { whenever(accounts.getTonSecrets(4)).thenReturn(fixture.tonSecret) }
        runBlocking {
            whenever(custodyDao.get(2)).thenReturn(
                WalletCustodyProvenance.watchMarker(fixture.rows[1].metaAccount, emptyList())
            )
        }
        whenever(preferences.keysWithPrefixes(any(), any(), any(), any(), any())).thenAnswer { invocation ->
            val prefixes = invocation.getArgument<Set<String>>(0)
            (fixture.namespace + "security_source_${fixture.v1Address}")
                .filterTo(linkedSetOf()) { key -> prefixes.any(key::startsWith) }
        }
    }

    private fun fixture(): Fixture {
        val legacyPair = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 7 }, emptyList()
        )
        val legacyAccount = legacyPair.publicKey.substrateAccountId()
        val legacyAddress = legacyAccount.toAddress(42)
        val legacySource = SecuritySource.Unspecified(legacyPair)
        val legacy = row(
            1, 0, "Legacy", true,
            substratePublic = legacyPair.publicKey,
            substrateAccount = legacyAccount,
            substrateCrypto = CryptoType.ED25519
        )
        val watchPublic = ByteArray(32) { 8 }
        val watch = row(
            2, 1, "Watch", false,
            substratePublic = watchPublic,
            substrateAccount = watchPublic.copyOf(),
            substrateCrypto = CryptoType.SR25519
        )
        val evmPair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32).also { it[31] = 1 })
        val evmSecret = EthereumSecrets(ethereumKeypair = evmPair)
        val chainPair = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 9 }, emptyList()
        )
        val chainAccount = chainPair.publicKey.substrateAccountId()
        val chainSecret = ChainAccountSecrets(chainPair, seed = ByteArray(32) { 9 })
        val chain = ChainAccountLocal(
            3, genesis, chainPair.publicKey, chainAccount,
            CryptoType.ED25519, "Imported", true
        )
        val evm = row(
            3, 2, "Standalone EVM", false,
            ethereumPublic = evmPair.publicKey,
            ethereumAddress = evmPair.publicKey.ethereumAddressFromPublicKey(),
            chains = listOf(chain),
            favorites = listOf(FavoriteChainLocal(3, genesis, true))
        )
        val phrase = "cluster notice abandon frost gospel boring element situate click mix vague replace " +
            "imitate garment useful crater resource dose tenant theme foam ancient phrase slight"
        val tonKey = PrivateKeyEd25519(Mnemonic.toSeed(phrase.split(' ')))
        val tonPublic = tonKey.publicKey().key.toByteArray()
        val tonSecret = TonSecrets(phrase.toByteArray(), Keypair(tonPublic, tonKey.key.toByteArray()))
        val ton = row(4, 3, "Native TON", false, tonPublic = tonPublic)
        val namespace = mutableSetOf(
            "3:ETHEREUM_SECRETS",
            "3:${chainAccount.toHexString()}:ACCESS_SECRETS",
            "4:TON_SECRETS"
        )
        return Fixture(
            listOf(ton, watch, evm, legacy), legacyAddress, legacySource,
            evmSecret, chainSecret, tonSecret, namespace
        )
    }

    private fun row(
        id: Long,
        position: Int,
        name: String,
        selected: Boolean,
        substratePublic: ByteArray? = null,
        substrateAccount: ByteArray? = null,
        substrateCrypto: CryptoType? = null,
        ethereumPublic: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        tonPublic: ByteArray? = null,
        chains: List<ChainAccountLocal> = emptyList(),
        favorites: List<FavoriteChainLocal> = emptyList()
    ): RelationJoinedMetaAccountInfo {
        val meta = MetaAccountLocal(
            substratePublicKey = substratePublic,
            substrateCryptoType = substrateCrypto,
            substrateAccountId = substrateAccount,
            ethereumPublicKey = ethereumPublic,
            ethereumAddress = ethereumAddress,
            tonPublicKey = tonPublic,
            name = name,
            isSelected = selected,
            position = position,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = true
        ).apply { this.id = id }
        return RelationJoinedMetaAccountInfo(meta, chains, favorites)
    }

    private class Fixture(
        val rows: List<RelationJoinedMetaAccountInfo>,
        val v1Address: String,
        val v1Source: SecuritySource,
        val evmSecret: jp.co.soramitsu.fearless_utils.scale.EncodableStruct<EthereumSecrets>,
        val chainSecret: jp.co.soramitsu.fearless_utils.scale.EncodableStruct<ChainAccountSecrets>,
        val tonSecret: jp.co.soramitsu.fearless_utils.scale.EncodableStruct<TonSecrets>,
        val namespace: MutableSet<String>
    ) {
        fun clear() {
            v1Source.keypair.privateKey.fill(0)
            evmSecret[EthereumSecrets.EthereumKeypair][KeyPairSchema.PrivateKey].fill(0)
            chainSecret[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey].fill(0)
            chainSecret[ChainAccountSecrets.Seed]?.fill(0)
            tonSecret[TonSecrets.PrivateKey].fill(0)
            tonSecret[TonSecrets.Seed].fill(0)
        }
    }

    private fun PortableWalletSemanticMaterial.Snapshot.countRole(targetRole: Int): Int =
        wallets.sumOf { wallet -> wallet.slots.count { it.role == targetRole } }
}
