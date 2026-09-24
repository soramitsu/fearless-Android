package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets as chainSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets as ethereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets as substrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets as tonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.WalletCustodyDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class PortableWalletMaterialPreflightTest {
    private val metaAccountDao = mock<MetaAccountDao>()
    private val accountRepository = mock<AccountRepository>()
    private val encryptedPreferences = mock<EncryptedPreferences>()
    private val custodyDao = mock<WalletCustodyDao>()
    private val journalStore = mock<WalletSecretMutationJournalStore>()
    private val preflight = PortableWalletMaterialPreflight(
        metaAccountDao, accountRepository, encryptedPreferences, custodyDao, journalStore
    )

    @Before
    fun setUp() {
        runBlocking { whenever(accountRepository.isWalletRecoveryRequired(any())).thenReturn(false) }
        whenever(encryptedPreferences.keysWithPrefixes(any(), any(), any(), any(), any()))
            .thenReturn(emptySet())
    }

    @Test
    fun `reads every root and per-chain key across distinct wallets`(): Unit = runBlocking {
        val first = wallet(
            id = 1, substrate = true, ethereum = true,
            chains = listOf(chain(1, "chain-a"), chain(1, "chain-b"))
        )
        val second = wallet(id = 2, ton = true)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(second, first))
        whenever(accountRepository.getSubstrateSecrets(1)).thenReturn(mock<EncodableStruct<SubstrateSecrets>>())
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())
        whenever(accountRepository.getTonSecrets(2)).thenReturn(mock<EncodableStruct<TonSecrets>>())
        whenever(accountRepository.getChainAccountSecrets(1, "chain-a"))
            .thenReturn(mock<EncodableStruct<ChainAccountSecrets>>())
        whenever(accountRepository.getChainAccountSecrets(1, "chain-b"))
            .thenReturn(mock<EncodableStruct<ChainAccountSecrets>>())

        assertEquals(PortableWalletMaterialPreflight.Coverage(2, 1, 1, 1, 2), preflight.verifyCoverage())

        verify(accountRepository).getSubstrateSecrets(1)
        verify(accountRepository).getEthereumSecrets(1)
        verify(accountRepository).getTonSecrets(2)
        verify(accountRepository).getChainAccountSecrets(1, "chain-a")
        verify(accountRepository).getChainAccountSecrets(1, "chain-b")
    }

    @Test
    fun `accepts a standalone EVM key without inventing a Substrate root`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(7, ethereum = true)))
        whenever(accountRepository.getEthereumSecrets(7)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertEquals(PortableWalletMaterialPreflight.Coverage(1, 0, 1, 0, 0), preflight.verifyCoverage())

        verify(accountRepository).getEthereumSecrets(7)
        verify(accountRepository, org.mockito.kotlin.never()).getSubstrateSecrets(7)
    }

    @Test
    fun `rejects a wallet with no persisted Substrate material`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(3, substrate = true)))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getSubstrateSecrets(3)
    }

    @Test
    fun `captures V1-only mnemonic seed path and exact keypair with original address`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        v1Keys("security_source_${fixture.address}")
        whenever(accountRepository.getSecuritySource(fixture.address)).thenReturn(fixture.source)

        assertEquals(
            PortableWalletMaterialPreflight.Coverage(1, 0, 0, 0, 0, 1),
            preflight.verifyCoverage()
        )
        val encoded = preflight.captureDraftPlaintext()
        val decoded = PortableWalletMaterialDraft.decode(encoded)
        try {
            val wallet = decoded.wallets.single()
            assertEquals(null, wallet.substrateSecret)
            val legacy = wallet.legacySubstrateSource!!
            assertEquals(fixture.address, legacy.accountAddress)
            assertEquals(PortableWalletMaterialDraft.SourceType.MNEMONIC, legacy.sourceType)
            assertEquals(fixture.mnemonic, legacy.mnemonic)
            assertEquals(fixture.path, legacy.derivationPath)
            assertArrayEquals(fixture.entropy, legacy.entropy)
            assertArrayEquals(fixture.seed, legacy.seed)
            assertArrayEquals(fixture.source.keypair.publicKey, legacy.publicKey)
            assertArrayEquals(fixture.source.keypair.privateKey, legacy.privateKey)
            assertArrayEquals(encoded, PortableWalletMaterialDraft.encode(decoded))
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `retains independent V1 source alongside V3 without replacing V3 bytes`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        v1Keys("security_source_${fixture.address}")
        whenever(accountRepository.getSecuritySource(fixture.address)).thenReturn(fixture.source)
        val v3 = substrateSecrets(
            substrateKeyPair = fixture.source.keypair,
            entropy = fixture.entropy,
            seed = fixture.seed,
            substrateDerivationPath = fixture.path
        )
        whenever(accountRepository.getSubstrateSecrets(1)).thenReturn(v3)

        assertEquals(
            PortableWalletMaterialPreflight.Coverage(1, 1, 0, 0, 0, 1),
            preflight.verifyCoverage()
        )
        val encoded = preflight.captureDraftPlaintext()
        val decoded = PortableWalletMaterialDraft.decode(encoded)
        try {
            assertArrayEquals(v3.toByteArray(), decoded.wallets.single().substrateSecret)
            assertEquals(fixture.address, decoded.wallets.single().legacySubstrateSource!!.accountAddress)
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `captures V1 direct key without inventing mnemonic entropy or seed`(): Unit = runBlocking {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { (it + 1).toByte() })
        val accountId = keypair.publicKey.substrateAccountId()
        val address = accountId.toAddress(0)
        val wallet = MetaAccountLocal(
            substratePublicKey = keypair.publicKey,
            substrateCryptoType = CryptoType.ECDSA,
            substrateAccountId = accountId,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = null,
            name = "Direct key",
            isSelected = true,
            position = 0,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = true
        ).apply { id = 1 }
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(
            listOf(RelationJoinedMetaAccountInfo(wallet, emptyList(), emptyList()))
        )
        v1Keys("security_source_$address")
        whenever(accountRepository.getSecuritySource(address)).thenReturn(SecuritySource.Unspecified(keypair))

        val encoded = preflight.captureDraftPlaintext()
        val decoded = PortableWalletMaterialDraft.decode(encoded)
        try {
            val legacy = decoded.wallets.single().legacySubstrateSource!!
            assertEquals(PortableWalletMaterialDraft.SourceType.UNSPECIFIED, legacy.sourceType)
            assertEquals(null, legacy.mnemonic)
            assertEquals(null, legacy.entropy)
            assertEquals(null, legacy.seed)
            assertEquals(null, legacy.derivationPath)
            assertArrayEquals(keypair.publicKey, legacy.publicKey)
            assertArrayEquals(keypair.privateKey, legacy.privateKey)
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `rejects two SS58 aliases for one V1 source before opening plaintext`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        val accountId = fixture.source.keypair.publicKey.substrateAccountId()
        v1Keys("security_source_${accountId.toAddress(0)}", "security_source_${accountId.toAddress(42)}")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
        verify(accountRepository, org.mockito.kotlin.never()).getSecuritySource(any())
    }

    @Test
    fun `rejects an active V1 address with no durable wallet owner`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        val unownedAddress = ByteArray(32) { 0x5a.toByte() }.toAddress(42)
        v1Keys("security_source_$unownedAddress")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
        verify(accountRepository, org.mockito.kotlin.never()).getSecuritySource(any())
    }

    @Test
    fun `rejects malformed active V1 aliases rather than skipping them`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))

        listOf("security_source_", "security_source_${"0".repeat(47)}").forEach { malformedKey ->
            v1Keys(malformedKey)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { preflight.verifyCoverage() }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { preflight.captureDraftPlaintext() }
            }
        }
        verify(accountRepository, org.mockito.kotlin.never()).getSecuritySource(any())
    }

    @Test
    fun `rejects a V1 key inventory changed while capturing material`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        whenever(encryptedPreferences.keysWithPrefixes(any(), any(), any(), any(), any()))
            .thenReturn(setOf("security_source_${fixture.address}"), emptySet())
        whenever(accountRepository.getSecuritySource(fixture.address)).thenReturn(fixture.source)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
        verify(accountRepository).getSecuritySource(fixture.address)
    }

    @Test
    fun `rejects ambiguous V1 ownership and invalid keypair even when alias is unique`(): Unit = runBlocking {
        val fixture = legacyFixture()
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(fixture.wallet))
        val badAddress = fixture.address.dropLast(1) + if (fixture.address.last() == '1') "2" else "1"
        v1Keys("security_source_$badAddress")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }

        v1Keys("security_source_${fixture.address}")
        val wrong = SecuritySource.Specified.Mnemonic(
            fixture.seed.copyOf(),
            jp.co.soramitsu.common.data.secrets.v1.Keypair(
                fixture.source.keypair.publicKey.copyOf(), ByteArray(32) { 7 }
            ),
            fixture.mnemonic,
            fixture.path
        )
        whenever(accountRepository.getSecuritySource(fixture.address)).thenReturn(wrong)
        assertThrows(Exception::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
    }

    @Test
    fun `rejects a missing independent EVM key even when Substrate is readable`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(wallet(4, substrate = true, ethereum = true)))
        whenever(accountRepository.getSubstrateSecrets(4)).thenReturn(mock<EncodableStruct<SubstrateSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getEthereumSecrets(4)
    }

    @Test
    fun `rejects a missing chain-specific key instead of silently dropping its address`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(wallet(5, chains = listOf(chain(5, "chain-a")))))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).getChainAccountSecrets(5, "chain-a")
    }

    @Test
    fun `rejects wallet metadata changed while secrets were read`(): Unit = runBlocking {
        val before = wallet(8, ethereum = true, name = "Before")
        val after = wallet(8, ethereum = true, name = "After")
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(before), listOf(after))
        whenever(accountRepository.getEthereumSecrets(8)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }
    }

    @Test
    fun `rejects favorite-chain changes during material validation`(): Unit = runBlocking {
        val before = wallet(8, ethereum = true, favorites = listOf(FavoriteChainLocal(8, "chain-a", true)))
        val after = wallet(8, ethereum = true, favorites = listOf(FavoriteChainLocal(8, "chain-a", false)))
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(before), listOf(after))
        whenever(accountRepository.getEthereumSecrets(8)).thenReturn(mock<EncodableStruct<EthereumSecrets>>())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }
    }

    @Test
    fun `rejects recovery-required wallets before opening any material`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(9, ton = true)))
        whenever(accountRepository.isWalletRecoveryRequired(9)).thenReturn(true)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verify(accountRepository).isWalletRecoveryRequired(9)
        verify(accountRepository, org.mockito.kotlin.never()).getTonSecrets(9)
    }

    @Test
    fun `rejects incomplete public identities without reading keys`(): Unit = runBlocking {
        val incomplete = MetaAccountLocal(
            substratePublicKey = byteArrayOf(1), substrateCryptoType = null,
            substrateAccountId = byteArrayOf(2), ethereumPublicKey = null,
            ethereumAddress = null, tonPublicKey = null, name = "Incomplete",
            isSelected = true, position = 0, isBackedUp = false,
            googleBackupAddress = null, initialized = true
        ).apply { id = 10 }
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(RelationJoinedMetaAccountInfo(incomplete, emptyList(), emptyList())))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.verifyCoverage() }
        }

        verifyNoInteractions(accountRepository)
    }

    @Test
    fun `captures all original roots and chain keys across separate wallets`(): Unit = runBlocking {
        val first = wallet(
            id = 1, substrate = true, ethereum = true,
            chains = listOf(chain(1, "chain-a"), chain(1, "chain-b")),
            favorites = listOf(FavoriteChainLocal(1, "chain-b", true))
        )
        val second = wallet(id = 2, ton = true)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(second, first))
        val substrate = substrateSecrets(
            substrateKeyPair = Keypair(byteArrayOf(1, 1), ByteArray(32) { 11 }, ByteArray(32) { 12 }),
            entropy = ByteArray(16) { 13 }, seed = ByteArray(64) { 14 }, substrateDerivationPath = "//hard"
        )
        val ethereum = ethereumSecrets(
            entropy = ByteArray(16) { 20 }, seed = ByteArray(64) { 21 },
            ethereumKeypair = Keypair(byteArrayOf(3, 1), ByteArray(32) { 22 }),
            ethereumDerivationPath = "m/44'/60'/0'/0/0"
        )
        val ton = tonSecrets("native ton words".toByteArray(), Keypair(byteArrayOf(5, 2), ByteArray(32) { 33 }))
        val chain = chainSecrets(
            keyPair = Keypair(byteArrayOf(6), ByteArray(32) { 44 }, ByteArray(32) { 45 }),
            entropy = ByteArray(16) { 46 }, seed = ByteArray(32) { 47 }, derivationPath = "//chain"
        )
        whenever(accountRepository.getSubstrateSecrets(1)).thenReturn(substrate)
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(ethereum)
        whenever(accountRepository.getTonSecrets(2)).thenReturn(ton)
        whenever(accountRepository.getChainAccountSecrets(1, "chain-a")).thenReturn(chain)
        whenever(accountRepository.getChainAccountSecrets(1, "chain-b")).thenReturn(chain)

        val encoded = preflight.captureDraftPlaintext()
        val decoded = PortableWalletMaterialDraft.decode(encoded)
        try {
            assertEquals(2, decoded.wallets.size)
            assertEquals(listOf(1L, 2L), decoded.wallets.map { it.identity.id })
            assertEquals(listOf("chain-a", "chain-b"), decoded.wallets[0].identity.chainAccounts.map { it.chainId })
            assertEquals(listOf("chain-b"), decoded.wallets[0].identity.favoriteChains.map { it.chainId })
            assertTrue(decoded.wallets[0].identity.isSelected)
            assertFalse(decoded.wallets[1].identity.isSelected)
            assertArrayEquals(substrate.toByteArray(), decoded.wallets[0].substrateSecret)
            assertArrayEquals(ethereum.toByteArray(), decoded.wallets[0].ethereumSecret)
            assertArrayEquals(ton.toByteArray(), decoded.wallets[1].tonSecret)
            assertArrayEquals(chain.toByteArray(), decoded.wallets[0].chainSecrets[0])
            assertArrayEquals(chain.toByteArray(), decoded.wallets[0].chainSecrets[1])
            assertEquals(null, decoded.wallets[1].substrateSecret)
            assertEquals(null, decoded.wallets[1].ethereumSecret)
            assertArrayEquals(encoded, PortableWalletMaterialDraft.encode(decoded))
            assertEquals("PortableWalletMaterialDraft.Snapshot(redacted)", decoded.toString())
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `captures standalone EVM key without synthesizing a Substrate mnemonic`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(1, ethereum = true)))
        val ethereum = ethereumSecrets(ethereumKeypair = Keypair(byteArrayOf(3, 1), ByteArray(32) { 99 }))
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(ethereum)

        val encoded = preflight.captureDraftPlaintext()
        val decoded = PortableWalletMaterialDraft.decode(encoded)
        try {
            assertEquals(null, decoded.wallets.single().substrateSecret)
            assertArrayEquals(ethereum.toByteArray(), decoded.wallets.single().ethereumSecret)
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `semantic capture retains selected watch wallets as non-signable public slots`(): Unit = runBlocking {
        val first = watchWallet(id = 10, selected = false, position = 3)
        val second = watchWallet(id = 11, selected = true, position = 1)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(first, second))
        whenever(custodyDao.get(10)).thenReturn(
            WalletCustodyProvenance.watchMarker(first.metaAccount, first.chainAccounts)
        )
        whenever(custodyDao.get(11)).thenReturn(
            WalletCustodyProvenance.watchMarker(second.metaAccount, second.chainAccounts)
        )

        val encoded = preflight.captureSemanticPlaintext()
        val decoded = PortableWalletSemanticMaterial.decode(encoded)
        try {
            assertEquals(2, decoded.wallets.size)
            assertEquals(0, decoded.selectedIndex)
            assertEquals(listOf(1L, 3L), decoded.wallets.map { it.sourcePosition })
            decoded.wallets.forEach { semanticWallet ->
                assertEquals(listOf(PortableWalletSemanticMaterial.Role.WATCH_IDENTITY),
                    semanticWallet.slots.map { it.role })
                assertFalse(semanticWallet.slots.single().fields.any {
                    it.id == PortableWalletSemanticMaterial.FieldId.PRIVATE_KEY
                })
            }
            assertArrayEquals(encoded, PortableWalletSemanticMaterial.encode(decoded))
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
        verify(accountRepository, org.mockito.kotlin.never()).getSubstrateSecrets(any())
    }

    @Test
    fun `semantic capture combines signed and watch wallets and marks verified signer`(): Unit = runBlocking {
        val signed = wallet(1, ethereum = true)
        val watch = watchWallet(id = 2, selected = false, position = 0)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(watch, signed))
        whenever(custodyDao.get(2)).thenReturn(
            WalletCustodyProvenance.watchMarker(watch.metaAccount, watch.chainAccounts)
        )
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(
            ethereumSecrets(ethereumKeypair = Keypair(byteArrayOf(3, 1), ByteArray(32) { 99 }))
        )

        val encoded = preflight.captureSemanticPlaintext()
        val decoded = PortableWalletSemanticMaterial.decode(encoded)
        try {
            assertEquals(2, decoded.wallets.size)
            assertEquals(1, decoded.selectedIndex)
            assertEquals(listOf(0L, 1L), decoded.wallets.map { it.sourcePosition })
            assertEquals(PortableWalletSemanticMaterial.Role.WATCH_IDENTITY, decoded.wallets[0].slots.single().role)
            assertEquals(PortableWalletSemanticMaterial.Role.EVM_ROOT, decoded.wallets[1].slots.first().role)
            verify(custodyDao).insert(org.mockito.kotlin.argThat {
                kind == WalletCustodyLocal.SIGNED && metaId == 1L
            })
        } finally {
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `semantic capture rejects unknown missing-key row and forged watch provenance`(): Unit = runBlocking {
        val watch = watchWallet(id = 1, selected = true, position = 0)
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(watch))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureSemanticPlaintext() }
        }

        val valid = WalletCustodyProvenance.watchMarker(watch.metaAccount, watch.chainAccounts)
        whenever(custodyDao.get(1)).thenReturn(valid)
        whenever(journalStore.hasSecretNamespace(1)).thenReturn(true)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureSemanticPlaintext() }
        }

        whenever(journalStore.hasSecretNamespace(1)).thenReturn(false)
        whenever(custodyDao.get(1)).thenReturn(
            WalletCustodyLocal(1, WalletCustodyLocal.WATCH, ByteArray(32) { 9 })
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { preflight.captureSemanticPlaintext() }
        }
    }

    @Test
    fun `semantic capture does not promote an unknown address-only Ethereum row to watch`(): Unit = runBlocking {
        val meta = MetaAccountLocal(
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = null,
            ethereumAddress = ByteArray(20) { 7 },
            tonPublicKey = null,
            name = "Historical address",
            isSelected = true,
            position = 0,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = true
        ).apply { id = 1 }
        whenever(metaAccountDao.getJoinedMetaAccountsInfo())
            .thenReturn(listOf(RelationJoinedMetaAccountInfo(meta, emptyList(), emptyList())))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { preflight.captureSemanticPlaintext() }
        }
        verify(custodyDao, org.mockito.kotlin.never()).insert(any())
    }

    @Test
    fun `capture rejects missing independent EVM root and changed wallet identity`(): Unit = runBlocking {
        val before = wallet(1, substrate = true, ethereum = true)
        val substrate = substrateSecrets(Keypair(byteArrayOf(1, 1), ByteArray(32) { 11 }))
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(before))
        whenever(accountRepository.getSubstrateSecrets(1)).thenReturn(substrate)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }

        val after = wallet(1, substrate = true, name = "Changed")
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(1, substrate = true)), listOf(after))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
    }

    @Test
    fun `capture rejects a secret bound to a different public key`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(1, ethereum = true)))
        whenever(accountRepository.getEthereumSecrets(1)).thenReturn(
            ethereumSecrets(ethereumKeypair = Keypair(byteArrayOf(9, 9), ByteArray(32) { 99 }))
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
    }

    @Test
    fun `capture never opens a recovery-required wallet root`(): Unit = runBlocking {
        whenever(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(wallet(1, ton = true)))
        whenever(accountRepository.isWalletRecoveryRequired(1)).thenReturn(true)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { preflight.captureDraftPlaintext() }
        }
        verify(accountRepository, org.mockito.kotlin.never()).getTonSecrets(1)
    }

    private fun wallet(
        id: Long,
        substrate: Boolean = false,
        ethereum: Boolean = false,
        ton: Boolean = false,
        chains: List<ChainAccountLocal> = emptyList(),
        favorites: List<FavoriteChainLocal> = emptyList(),
        name: String = "Wallet"
    ): RelationJoinedMetaAccountInfo {
        val meta = MetaAccountLocal(
            substratePublicKey = if (substrate) byteArrayOf(1, id.toByte()) else null,
            substrateCryptoType = if (substrate) CryptoType.SR25519 else null,
            substrateAccountId = if (substrate) byteArrayOf(2, id.toByte()) else null,
            ethereumPublicKey = if (ethereum) byteArrayOf(3, id.toByte()) else null,
            ethereumAddress = if (ethereum) byteArrayOf(4, id.toByte()) else null,
            tonPublicKey = if (ton) byteArrayOf(5, id.toByte()) else null,
            name = name, isSelected = id == 1L, position = id.toInt(),
            isBackedUp = false, googleBackupAddress = null, initialized = true
        ).apply { this.id = id }
        return RelationJoinedMetaAccountInfo(meta, chains, favorites)
    }

    private fun chain(metaId: Long, chainId: String) = ChainAccountLocal(
        metaId, chainId, byteArrayOf(6), byteArrayOf(7), CryptoType.ED25519,
        "Chain account", true
    )

    private fun watchWallet(
        id: Long,
        selected: Boolean,
        position: Int
    ): RelationJoinedMetaAccountInfo {
        val publicKey = ByteArray(32) { id.toByte() }
        val meta = MetaAccountLocal(
            substratePublicKey = publicKey,
            substrateCryptoType = CryptoType.SR25519,
            substrateAccountId = publicKey.copyOf(),
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = null,
            name = "Watch $id",
            isSelected = selected,
            position = position,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = true
        ).apply { this.id = id }
        return RelationJoinedMetaAccountInfo(meta, emptyList(), emptyList())
    }

    private fun v1Keys(vararg keys: String) {
        whenever(encryptedPreferences.keysWithPrefixes(any(), any(), any(), any(), any()))
            .thenReturn(keys.toSet())
    }

    private data class LegacyFixture(
        val wallet: RelationJoinedMetaAccountInfo,
        val address: String,
        val source: SecuritySource.Specified.Mnemonic,
        val entropy: ByteArray,
        val seed: ByteArray,
        val mnemonic: String,
        val path: String
    )

    private fun legacyFixture(): LegacyFixture {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy).words
        val path = "//hard"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(mnemonic, decodedPath.password).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ED25519,
            seed = seed,
            junctions = decodedPath.junctions
        )
        val accountId = keypair.publicKey.substrateAccountId()
        val wallet = MetaAccountLocal(
            substratePublicKey = keypair.publicKey,
            substrateCryptoType = CryptoType.ED25519,
            substrateAccountId = accountId,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = null,
            name = "Legacy",
            isSelected = true,
            position = 0,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = true
        ).apply { id = 1 }
        return LegacyFixture(
            RelationJoinedMetaAccountInfo(wallet, emptyList(), emptyList()),
            accountId.toAddress(42),
            SecuritySource.Specified.Mnemonic(seed, keypair, mnemonic, path),
            entropy,
            seed,
            mnemonic,
            path
        )
    }
}
