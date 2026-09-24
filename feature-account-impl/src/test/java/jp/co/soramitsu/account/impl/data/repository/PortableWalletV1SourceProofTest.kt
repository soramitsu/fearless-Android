package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletChainSigningProof.ApprovedGenesis
import jp.co.soramitsu.account.impl.data.repository.PortableWalletChainSigningProof.IdentityKind
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableWalletV1SourceProofTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val genesis = "0x" + "01".repeat(32)

    @Test
    fun `historical mnemonic source proves its address key seed path and entropy`() {
        val source = snapshot(listOf(legacyMnemonicSlot()))
        val encoded = codec.encode(source)
        try {
            val counts = PortableWalletV1SourceProof.verify(encoded)
            assertEquals(1, counts.wallets)
            assertEquals(1, counts.provedLegacySources)
            assertEquals(0, counts.otherSlots)
            assertEquals("PortableWalletV1SourceProof.Counts(redacted)", counts.toString())
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `historical create seed JSON and unspecified recipes retain their original key proof`() {
        assertProved(withField(legacyMnemonicSlot(), field.SOURCE_RECIPE, byteArrayOf(1)))
        assertProved(legacyKeyOnlySlot(recipe = 2, includeSeed = true))
        assertProved(legacyKeyOnlySlot(recipe = 3, includeSeed = false))
        assertProved(legacyKeyOnlySlot(recipe = 5, includeSeed = false))
        assertProved(legacyKeyOnlySlot(recipe = 5, includeSeed = false, encryption = EncryptionType.ECDSA))
    }

    @Test
    fun `tampered V1 entropy private key address or recipe cannot be proved`() {
        val slot = legacyMnemonicSlot()
        try {
            assertRejected(withField(slot, field.ENTROPY, ByteArray(16) { 8 }))
            assertRejected(withField(slot, field.PRIVATE_KEY, ByteArray(32) { 9 }))
            assertRejected(
                withField(
                    slot, field.ACCOUNT_ID_OR_ADDRESS,
                    ByteArray(32) { 7 }.toAddress(42).toByteArray()
                )
            )
            assertRejected(withField(slot, field.SOURCE_RECIPE, byteArrayOf(3)))
        } finally {
            slot.clearSecrets()
        }
    }

    @Test
    fun `V1 fallback cannot disagree with the same wallets V3 Substrate identity`() {
        val legacy = legacyMnemonicSlot()
        val publicKey = legacy.fields.single { it.id == field.PUBLIC_KEY }.value
        val privateKey = legacy.fields.single { it.id == field.PRIVATE_KEY }.value
        val accountId = legacy.fields.single { it.id == field.ACCOUNT_ID_OR_ADDRESS }.value
            .toString(Charsets.UTF_8).toAccountId()
        val root = slot(
            role.SUBSTRATE_ROOT, "",
            item(field.PUBLIC_KEY, publicKey),
            item(field.PRIVATE_KEY, privateKey),
            item(field.ACCOUNT_ID_OR_ADDRESS, accountId),
            item(field.CRYPTO_TYPE, byteArrayOf(2)),
            item(field.SOURCE_RECIPE, byteArrayOf(0))
        )
        val source = snapshot(listOf(root, legacy))
        val encoded = codec.encode(source)
        try {
            assertEquals(1, PortableWalletV1SourceProof.verify(encoded).provedLegacySources)
            assertRejected(listOf(withField(root, field.PUBLIC_KEY, ByteArray(32) { 9 }), legacy))
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `V1 and V2 sources are independently proved in a mixed historical cohort`() {
        val legacy = legacyMnemonicSlot()
        val chainSeed = ByteArray(32) { 5 }
        val chainPair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, chainSeed, emptyList())
        val account = chainPair.publicKey.substrateAccountId()
        val raw = ChainAccountSecrets(chainPair, seed = chainSeed, derivationPath = "").toByteArray()
        val chain = slot(
            role.CHAIN_ACCOUNT, genesis,
            item(field.PUBLIC_KEY, chainPair.publicKey),
            item(field.PRIVATE_KEY, chainPair.privateKey),
            item(field.SEED, chainSeed),
            item(field.DERIVATION_PATH, byteArrayOf()),
            item(field.ACCOUNT_ID_OR_ADDRESS, account),
            item(field.CRYPTO_TYPE, byteArrayOf(2)),
            item(field.CHAIN_NAME, byteArrayOf()),
            item(field.INITIALIZED_OR_FAVORITE, byteArrayOf(1)),
            item(field.SOURCE_RECIPE, byteArrayOf(0))
        )
        val original = slot(
            role.AUXILIARY_SOURCE, "0000",
            item(field.SOURCE_RECIPE, byteArrayOf(0)),
            item(field.SOURCE_PLATFORM, byteArrayOf(1)),
            item(field.SOURCE_SLOT_ROLE, byteArrayOf(14)),
            item(field.BINDING_KIND, byteArrayOf(5)),
            item(field.BINDING_CHAIN_ID, genesis.toByteArray()),
            item(field.SOURCE_FORMAT, byteArrayOf(3)),
            item(field.SOURCE_BYTES, raw),
            item(field.BINDING_ACCOUNT_ID, account)
        )
        val snapshot = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { 1 }, 0, true, "V1+V2", emptyList(), listOf(legacy, chain, original)
                )
            )
        )
        val encoded = codec.encode(snapshot)
        try {
            val legacyCounts = PortableWalletV1SourceProof.verify(encoded)
            assertEquals(1, legacyCounts.wallets)
            assertEquals(1, legacyCounts.provedLegacySources)
            assertEquals(2, legacyCounts.otherSlots)
            val chainCounts = PortableWalletChainSigningProof.verify(
                encoded, listOf(ApprovedGenesis(genesis, IdentityKind.SUBSTRATE))
            )
            assertEquals(1, chainCounts.chainAccounts)
            assertEquals(1, chainCounts.exactOriginalSources)
        } finally {
            snapshot.clearSecrets()
            encoded.fill(0)
            raw.fill(0)
            chainSeed.fill(0)
        }
    }

    private fun legacyMnemonicSlot(): PortableWalletSemanticMaterial.Slot {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy).words
        val path = "//hard"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(mnemonic, decodedPath.password).seed
        val pair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed, decodedPath.junctions)
        val result = slot(
            role.LEGACY_SUBSTRATE, "",
            item(field.PUBLIC_KEY, pair.publicKey),
            item(field.PRIVATE_KEY, pair.privateKey),
            item(field.ENTROPY, entropy),
            item(field.SEED, seed),
            item(field.DERIVATION_PATH, path.toByteArray()),
            item(field.ACCOUNT_ID_OR_ADDRESS, pair.publicKey.substrateAccountId().toAddress(42).toByteArray()),
            item(field.CRYPTO_TYPE, byteArrayOf(2)),
            item(field.SOURCE_RECIPE, byteArrayOf(4)),
            item(field.MNEMONIC, mnemonic.toByteArray())
        )
        entropy.fill(0)
        seed.fill(0)
        return result
    }

    private fun legacyKeyOnlySlot(
        recipe: Int,
        includeSeed: Boolean,
        encryption: EncryptionType = EncryptionType.ED25519
    ): PortableWalletSemanticMaterial.Slot {
        val seed = ByteArray(32) { 7 }
        val pair = SubstrateKeypairFactory.generate(encryption, seed, emptyList())
        val cryptoType = when (encryption) {
            EncryptionType.SR25519 -> 1
            EncryptionType.ED25519 -> 2
            EncryptionType.ECDSA -> 3
            else -> error("Unsupported test encryption")
        }
        val result = slot(
            role.LEGACY_SUBSTRATE, "",
            *listOfNotNull(
                item(field.PUBLIC_KEY, pair.publicKey),
                item(field.PRIVATE_KEY, pair.privateKey),
                (pair as? Sr25519Keypair)?.nonce?.let { item(field.NONCE, it) },
                if (includeSeed) item(field.SEED, seed) else null,
                item(field.ACCOUNT_ID_OR_ADDRESS, pair.publicKey.substrateAccountId().toAddress(42).toByteArray()),
                item(field.CRYPTO_TYPE, byteArrayOf(cryptoType.toByte())),
                item(field.SOURCE_RECIPE, byteArrayOf(recipe.toByte()))
            ).toTypedArray()
        )
        seed.fill(0)
        return result
    }

    private fun assertProved(candidate: PortableWalletSemanticMaterial.Slot) {
        val source = snapshot(listOf(candidate))
        val encoded = codec.encode(source)
        try {
            assertEquals(1, PortableWalletV1SourceProof.verify(encoded).provedLegacySources)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun assertRejected(candidate: PortableWalletSemanticMaterial.Slot) = assertRejected(listOf(candidate))

    private fun assertRejected(slots: List<PortableWalletSemanticMaterial.Slot>) {
        val source = snapshot(slots)
        val encoded = try {
            codec.encode(source)
        } catch (_: IllegalArgumentException) {
            source.clearSecrets()
            return
        }
        try {
            assertThrows(Exception::class.java) { PortableWalletV1SourceProof.verify(encoded) }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun snapshot(slots: List<PortableWalletSemanticMaterial.Slot>) = PortableWalletSemanticMaterial.Snapshot(
        0,
        listOf(
            PortableWalletSemanticMaterial.Wallet(ByteArray(16) { 1 }, 0, true, "V1", emptyList(), slots)
        )
    )

    private fun withField(
        source: PortableWalletSemanticMaterial.Slot,
        id: Int,
        bytes: ByteArray
    ) = slot(
        source.role, source.key, *source.fields.map { if (it.id == id) item(id, bytes) else it }.toTypedArray()
    )

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field
    ) = PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })

    private fun item(id: Int, bytes: ByteArray) = PortableWalletSemanticMaterial.Field(id, bytes.copyOf())
}
