package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

class PortableWalletV3OriginalSourceProofTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `exact Android V3 source validates and proves original EVM signing and export bytes`() {
        val fixture = fixture()
        val encoded = codec.encode(fixture)
        try {
            val proof = PortableWalletV3OriginalSourceProof.verify(encoded)
            assertEquals(1, proof.wallets)
            assertEquals(1, proof.exactAndroidRoots)
            assertEquals("PortableWalletV3OriginalSourceProof.Counts(redacted)", proof.toString())
        } finally {
            fixture.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `exact Android Substrate and native TON originals preserve signing recovery and export`() {
        val substrate = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 7 }, emptyList()
        )
        val substrateRaw = SubstrateSecrets(substrateKeyPair = substrate).toByteArray()
        val substrateRoot = slot(
            role.SUBSTRATE_ROOT, "",
            item(field.PUBLIC_KEY, substrate.publicKey),
            item(field.PRIVATE_KEY, substrate.privateKey),
            item(field.ACCOUNT_ID_OR_ADDRESS, substrate.publicKey),
            item(field.CRYPTO_TYPE, byteArrayOf(2)),
            item(field.SOURCE_RECIPE, byteArrayOf(0))
        )
        val substrateOriginal = original(0, 11, 2, substrateRaw)
        val phrase = "cluster notice abandon frost gospel boring element situate click mix vague replace " +
            "imitate garment useful crater resource dose tenant theme foam ancient phrase slight"
        val tonKey = PrivateKeyEd25519(Mnemonic.toSeed(phrase.split(' ')))
        val tonPrivate = tonKey.key.toByteArray()
        val tonPublic = tonKey.publicKey().key.toByteArray()
        val tonRaw = TonSecrets(phrase.toByteArray(), Keypair(tonPublic, tonPrivate)).toByteArray()
        val tonAddress = byteArrayOf(0) + tonPublic.tonAccountId(false).substring(2).chunked(2).map {
            it.toInt(16).toByte()
        }
        val tonRoot = slot(
            role.TON_ROOT, "",
            item(field.PUBLIC_KEY, tonPublic),
            item(field.PRIVATE_KEY, tonPrivate),
            item(field.SEED, phrase.toByteArray()),
            item(field.ACCOUNT_ID_OR_ADDRESS, tonAddress),
            item(field.SOURCE_RECIPE, byteArrayOf(0)),
            item(field.TON_CONTRACT_VERSION, byteArrayOf(2)),
            item(field.TON_ADDRESS_ENCODING, byteArrayOf(1))
        )
        val tonOriginal = original(1, 13, 4, tonRaw)
        val snapshot = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 1 }, 0, true, "Wallet",
                    emptyList(), listOf(substrateRoot, tonRoot, substrateOriginal, tonOriginal)
                )
            )
        )
        val encoded = codec.encode(snapshot)
        val tonOnly = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 2 }, 0, true, "TON",
                    emptyList(), listOf(tonRoot, original(0, 13, 4, tonRaw))
                )
            )
        )
        val tonOnlyEncoded = codec.encode(tonOnly)
        try {
            assertEquals(2, PortableWalletV3OriginalSourceProof.verify(encoded).exactAndroidRoots)
            assertEquals(1, PortableWalletV3OriginalSourceProof.verify(tonOnlyEncoded).exactAndroidRoots)
            assertRejected(
                listOf(
                    substrateRoot,
                    slot(
                        role.TON_ROOT, "",
                        *(tonRoot.fields + item(field.MNEMONIC, phrase.toByteArray())).toTypedArray()
                    ),
                    substrateOriginal, tonOriginal
                )
            )
        } finally {
            snapshot.clearSecrets()
            tonOnly.clearSecrets()
            encoded.fill(0)
            tonOnlyEncoded.fill(0)
            substrateRaw.fill(0)
            tonRaw.fill(0)
            tonPrivate.fill(0)
        }
    }

    @Test
    fun `missing or substituted V3 original and changed semantic private key fail closed`() {
        val fixture = fixture()
        val wallet = fixture.wallets.single()
        val root = wallet.slots.first()
        val original = wallet.slots.last()
        try {
            assertRejected(wallet.slots.take(1))
            assertRejected(listOf(root, withField(original, field.SOURCE_BYTES, ByteArray(32) { 9 })))
            assertRejected(listOf(withField(root, field.PRIVATE_KEY, ByteArray(32) { 7 }), original))
            assertRejected(listOf(withField(root, field.SOURCE_RECIPE, byteArrayOf(1)), original))
            assertRejected(listOf(root, withField(original, field.SOURCE_SLOT_ROLE, byteArrayOf(11))))
        } finally {
            fixture.clearSecrets()
        }
    }

    @Test
    fun `unsupported source platforms and non-root wallet slots cannot become complete evidence`() {
        val fixture = fixture()
        val wallet = fixture.wallets.single()
        val root = wallet.slots.first()
        val original = wallet.slots.last()
        try {
            assertRejected(listOf(root, withField(original, field.SOURCE_PLATFORM, byteArrayOf(2))))
            assertRejected(
                listOf(
                    root, original,
                    slot(
                        role.FAVORITE_CHAIN, "chain-x",
                        item(field.INITIALIZED_OR_FAVORITE, byteArrayOf(1))
                    )
                )
            )
        } finally {
            fixture.clearSecrets()
        }
    }

    @Test
    fun `mixed signed and watch cohort accounts for every Android original source`() {
        val fixture = fixture()
        val signed = fixture.wallets.single()
        val favorite = slot(
            role.FAVORITE_CHAIN, "chain-x",
            item(field.INITIALIZED_OR_FAVORITE, byteArrayOf(1))
        )
        val watch = slot(
            role.WATCH_IDENTITY, "0000",
            item(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 4 }),
            item(field.WATCH_ECOSYSTEM, byteArrayOf(2))
        )
        val cohort = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    signed.portableId.copyOf(), signed.sourcePosition, signed.initialized,
                    signed.name, emptyList(), listOf(signed.slots.first(), favorite, signed.slots.last())
                ),
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 2 }, 1, true, "Watch", emptyList(), listOf(watch)
                )
            )
        )
        val encoded = codec.encode(cohort)
        try {
            val counts = PortableWalletAndroidSourceCohortProof.verify(encoded, emptyList())
            assertEquals(2, counts.wallets)
            assertEquals(1, counts.signedWallets)
            assertEquals(1, counts.watchWallets)
            assertEquals(1, counts.v3Roots)
            assertEquals(1, counts.publicFavorites)
            assertEquals(0, counts.v1Sources)
            assertEquals(0, counts.v2Chains)
            val unusedPolicy = PortableWalletChainSigningProof.ApprovedGenesis(
                "0x" + "01".repeat(32), PortableWalletChainSigningProof.IdentityKind.SUBSTRATE
            )
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletAndroidSourceCohortProof.verify(encoded, listOf(unusedPolicy))
            }
        } finally {
            encoded.fill(0)
            cohort.clearSecrets()
            fixture.clearSecrets()
        }
    }

    @Test
    fun `cohort rejects a watch mixed with a signer and an unproved foreign source`() {
        val fixture = fixture()
        val signed = fixture.wallets.single()
        val watch = slot(
            role.WATCH_IDENTITY, "0000",
            item(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 4 }),
            item(field.WATCH_ECOSYSTEM, byteArrayOf(2))
        )
        val unproved = slot(
            role.AUXILIARY_SOURCE, "0001",
            item(field.SOURCE_RECIPE, byteArrayOf(0)),
            item(field.SOURCE_PLATFORM, byteArrayOf(2)),
            item(field.SOURCE_SLOT_ROLE, byteArrayOf(2)),
            item(field.BINDING_KIND, byteArrayOf(3)),
            item(field.SOURCE_FORMAT, byteArrayOf(1)),
            item(field.SOURCE_BYTES, byteArrayOf(1))
        )
        try {
            assertCohortRejected(signed.slots + watch)
            assertCohortRejected(signed.slots + unproved)
        } finally {
            fixture.clearSecrets()
            watch.clearSecrets()
            unproved.clearSecrets()
        }
    }

    private fun assertCohortRejected(slots: List<PortableWalletSemanticMaterial.Slot>) {
        val wallet = PortableWalletSemanticMaterial.Wallet(
            ByteArray(16) { 1 }, 0, true, "Wallet", emptyList(),
            slots.sortedWith(compareBy({ it.role }, { it.key }))
        )
        val snapshot = PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet))
        val encoded = codec.encode(snapshot)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletAndroidSourceCohortProof.verify(encoded, emptyList())
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun fixture(): PortableWalletSemanticMaterial.Snapshot {
        val pair = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { (it + 1).toByte() })
        val raw = EthereumSecrets(ethereumKeypair = pair).toByteArray()
        val root = slot(
            role.EVM_ROOT, "",
            item(field.PUBLIC_KEY, pair.publicKey),
            item(field.PRIVATE_KEY, pair.privateKey),
            item(field.ACCOUNT_ID_OR_ADDRESS, pair.publicKey.ethereumAddressFromPublicKey()),
            item(field.SOURCE_RECIPE, byteArrayOf(0))
        )
        val original = original(0, 12, 3, raw)
        raw.fill(0)
        return PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 1 }, 0, true, "Wallet",
                    emptyList(), listOf(root, original)
                )
            )
        )
    }

    private fun original(
        ordinal: Int,
        sourceRole: Int,
        binding: Int,
        raw: ByteArray
    ) = slot(
        role.AUXILIARY_SOURCE, ordinal.toString(16).padStart(4, '0'),
        item(field.SOURCE_RECIPE, byteArrayOf(0)),
        item(field.SOURCE_PLATFORM, byteArrayOf(1)),
        item(field.SOURCE_SLOT_ROLE, byteArrayOf(sourceRole.toByte())),
        item(field.BINDING_KIND, byteArrayOf(binding.toByte())),
        item(field.SOURCE_FORMAT, byteArrayOf(4)),
        item(field.SOURCE_BYTES, raw)
    )

    private fun assertRejected(slots: List<PortableWalletSemanticMaterial.Slot>) {
        val source = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(ByteArray(16) { 1 }, 0, true, "Wallet", emptyList(), slots)
            )
        )
        val encoded = try {
            codec.encode(source)
        } catch (_: IllegalArgumentException) {
            return
        }
        try {
            assertThrows(Exception::class.java) { PortableWalletV3OriginalSourceProof.verify(encoded) }
        } finally {
            encoded.fill(0)
        }
    }

    private fun withField(
        source: PortableWalletSemanticMaterial.Slot,
        id: Int,
        value: ByteArray
    ): PortableWalletSemanticMaterial.Slot = slot(
        source.role, source.key,
        *source.fields.map { if (it.id == id) item(id, value) else it }.toTypedArray()
    )

    private fun slot(
        role: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field
    ) = PortableWalletSemanticMaterial.Slot(role, key, fields.sortedBy { it.id })

    private fun item(id: Int, bytes: ByteArray) = PortableWalletSemanticMaterial.Field(id, bytes.copyOf())
}
