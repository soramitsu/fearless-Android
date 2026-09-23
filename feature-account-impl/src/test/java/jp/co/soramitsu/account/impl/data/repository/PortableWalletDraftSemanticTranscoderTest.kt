package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toByteArray
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets as chainSecrets
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets as ethereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets as substrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets as tonSecrets

class PortableWalletDraftSemanticTranscoderTest {
    private val draft = PortableWalletMaterialDraft
    private val semantic = PortableWalletSemanticMaterial
    private val transcode = PortableWalletDraftSemanticTranscoder
    private val field = PortableWalletSemanticMaterial.FieldId
    private val role = PortableWalletSemanticMaterial.Role

    @Test
    fun `standalone EVM retains semantic derivation and byte-identical V3 source`() {
        val evmPublic = byteArrayOf(3, 7)
        val address = byteArrayOf(4, 7)
        val privateKey = ByteArray(32) { 0x63 }
        val entropy = ByteArray(16) { 0x17 }
        val seed = ByteArray(64) { 0x27 }
        val raw =
            ethereumSecrets(
                entropy = entropy,
                seed = seed,
                ethereumKeypair = Keypair(evmPublic, privateKey),
                ethereumDerivationPath = "m/44'/60'/0'/0/0",
            ).toByteArray()
        val source =
            PortableWalletMaterialDraft.Snapshot(
                listOf(
                    wallet(
                        id = 7,
                        selected = true,
                        position = 4,
                        ethereumPublic = evmPublic,
                        ethereumAddress = address,
                        ethereumRaw = raw,
                    ),
                ),
            )
        val projected = transcode.toSemanticSnapshot(source)
        val draftBytes = draft.encode(source)
        val encoded = semantic.encode(projected)
        try {
            val wallet = projected.wallets.single()
            assertEquals("16d6fcf693a24f2a205faa6e72f5da03", wallet.portableId.hex())
            assertNotEquals(address.hex(), wallet.portableId.hex())
            assertEquals(0, projected.selectedIndex)
            assertEquals(4L, wallet.sourcePosition)
            assertEquals(listOf(role.EVM_ROOT, role.AUXILIARY_SOURCE), wallet.slots.map { it.role })
            val root = wallet.slots[0]
            assertArrayEquals(evmPublic, root.value(field.PUBLIC_KEY))
            assertArrayEquals(privateKey, root.value(field.PRIVATE_KEY))
            assertArrayEquals(entropy, root.value(field.ENTROPY))
            assertArrayEquals(seed, root.value(field.SEED))
            assertEquals("m/44'/60'/0'/0/0", root.value(field.DERIVATION_PATH).toString(Charsets.UTF_8))
            assertArrayEquals(address, root.value(field.ACCOUNT_ID_OR_ADDRESS))
            val aux = wallet.slots[1]
            assertEquals("0000", aux.key)
            assertArrayEquals(raw, aux.value(field.SOURCE_BYTES))
            assertArrayEquals(byteArrayOf(12), aux.value(field.SOURCE_SLOT_ROLE))
            assertArrayEquals(byteArrayOf(3), aux.value(field.BINDING_KIND))
            assertArrayEquals(byteArrayOf(4), aux.value(field.SOURCE_FORMAT))
            val fromPlaintext = transcode.encodeDraftPlaintext(draftBytes)
            val decoded = semantic.decode(encoded)
            try {
                assertArrayEquals(encoded, fromPlaintext)
                assertArrayEquals(encoded, semantic.encode(decoded))
            } finally {
                fromPlaintext.fill(0)
                decoded.clearSecrets()
            }
            assertArrayEquals(raw, source.wallets.single().ethereumSecret)
        } finally {
            projected.clearSecrets()
            source.clearSecrets()
            draftBytes.fill(0)
            encoded.fill(0)
        }
    }

    @Test
    fun `mixed roots TON chains favorites and selected presentation order survive`() {
        val substratePublic = byteArrayOf(1, 20)
        val substrateAccount = byteArrayOf(2, 20)
        val substrateRaw =
            substrateSecrets(
                Keypair(substratePublic, ByteArray(32) { 11 }, ByteArray(32) { 12 }),
                entropy = ByteArray(16) { 13 },
                seed = ByteArray(64) { 14 },
                substrateDerivationPath = "//hard",
            ).toByteArray()
        val evmPublic = byteArrayOf(3, 20)
        val evmAddress = byteArrayOf(4, 20)
        val evmRaw = ethereumSecrets(ethereumKeypair = Keypair(evmPublic, ByteArray(32) { 22 })).toByteArray()
        val tonPublic = ByteArray(32) { (it + 1).toByte() }
        val tonRaw =
            tonSecrets(
                "native ton words".toByteArray(),
                Keypair(tonPublic, ByteArray(64) { 33 }),
            ).toByteArray()
        val chainPublic = byteArrayOf(6, 20)
        val chainAccount = byteArrayOf(7, 20)
        val chainRaw =
            chainSecrets(
                Keypair(chainPublic, ByteArray(32) { 44 }, ByteArray(32) { 45 }),
                entropy = ByteArray(16) { 46 },
                seed = ByteArray(32) { 47 },
                derivationPath = "//chain",
            ).toByteArray()
        val first =
            wallet(
                id = 10,
                selected = false,
                position = 5,
                ethereumPublic = byteArrayOf(3, 10),
                ethereumAddress = byteArrayOf(4, 10),
                ethereumRaw =
                    ethereumSecrets(
                        ethereumKeypair =
                            Keypair(byteArrayOf(3, 10), ByteArray(32) { 10 }),
                    ).toByteArray(),
            )
        val second =
            wallet(
                id = 20, selected = true, position = 0,
                substratePublic = substratePublic, substrateAccount = substrateAccount,
                substrateRaw = substrateRaw, substrateCrypto = CryptoType.SR25519,
                ethereumPublic = evmPublic, ethereumAddress = evmAddress, ethereumRaw = evmRaw,
                tonPublic = tonPublic, tonRaw = tonRaw,
                chains =
                    listOf(
                        PortableWalletMaterialDraft.ChainIdentity(
                            "chain-a", b64(chainPublic), b64(chainAccount),
                            CryptoType.ED25519.name, "Chain", true,
                        ) to chainRaw,
                    ),
                favorites =
                    listOf(
                        PortableWalletMaterialDraft.FavoriteIdentity("chain-a", true),
                        PortableWalletMaterialDraft.FavoriteIdentity("chain-b", false),
                    ),
            )
        val source = PortableWalletMaterialDraft.Snapshot(listOf(first, second)) // Draft order is durable ID, not presentation order.
        val projected = transcode.toSemanticSnapshot(source)
        try {
            assertEquals(listOf(0L, 5L), projected.wallets.map { it.sourcePosition })
            assertEquals(0, projected.selectedIndex)
            assertEquals("b2f22b5354a6cb386b87ba7fba20aa34", projected.wallets[0].portableId.hex())
            val slots = projected.wallets[0].slots
            assertEquals(listOf(1, 2, 3, 5, 6, 6, 7, 7, 7, 7), slots.map { it.role })
            assertArrayEquals(byteArrayOf(1), slots[0].value(field.CRYPTO_TYPE))
            assertArrayEquals(ByteArray(32) { 12 }, slots[0].value(field.NONCE))
            assertArrayEquals(byteArrayOf(2), slots[3].value(field.CRYPTO_TYPE))
            assertArrayEquals(ByteArray(32) { 45 }, slots[3].value(field.NONCE))
            assertArrayEquals(ByteArray(16) { 46 }, slots[3].value(field.ENTROPY))
            assertArrayEquals(ByteArray(32) { 47 }, slots[3].value(field.SEED))
            assertEquals("//chain", slots[3].value(field.DERIVATION_PATH).toString(Charsets.UTF_8))
            assertArrayEquals(substrateRaw, slots[6].value(field.SOURCE_BYTES))
            assertArrayEquals(evmRaw, slots[7].value(field.SOURCE_BYTES))
            assertArrayEquals(tonRaw, slots[8].value(field.SOURCE_BYTES))
            assertArrayEquals(chainRaw, slots[9].value(field.SOURCE_BYTES))
            assertArrayEquals(byteArrayOf(14), slots[9].value(field.SOURCE_SLOT_ROLE))
            assertEquals("chain-a", slots[9].value(field.BINDING_CHAIN_ID).toString(Charsets.UTF_8))
            assertArrayEquals(chainAccount, slots[9].value(field.BINDING_ACCOUNT_ID))
            assertArrayEquals(
                slots[3].value(field.ACCOUNT_ID_OR_ADDRESS),
                slots[9].value(field.BINDING_ACCOUNT_ID),
            )
            assertArrayEquals(byteArrayOf(1), slots[4].value(field.INITIALIZED_OR_FAVORITE))
            assertArrayEquals(byteArrayOf(0), slots[5].value(field.INITIALIZED_OR_FAVORITE))
            assertArrayEquals(byteArrayOf(2), slots[2].value(field.TON_CONTRACT_VERSION))
            assertArrayEquals(byteArrayOf(1), slots[2].value(field.TON_ADDRESS_ENCODING))
            assertArrayEquals(ByteArray(64) { 33 }, slots[2].value(field.PRIVATE_KEY))
            assertArrayEquals("native ton words".toByteArray(), slots[2].value(field.SEED))
            val tonAddress = slots[2].value(field.ACCOUNT_ID_OR_ADDRESS)
            assertEquals(33, tonAddress.size)
            assertEquals(0, tonAddress[0].toInt())
            assertEquals("0:${tonAddress.drop(1).toByteArray().hex()}", tonPublic.tonAccountId(false))
            assertSemanticRoundtrip(projected)
        } finally {
            projected.clearSecrets()
            source.clearSecrets()
        }
    }

    @Test
    fun `V1 source keeps original mnemonic entropy seed path keypair and address without fake raw slot`() {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy).words
        val path = "//hard"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(mnemonic, decodedPath.password).seed
        val keypair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, seed, decodedPath.junctions)
        val accountId = keypair.publicKey.substrateAccountId()
        val address = accountId.toAddress(42)
        val legacy =
            PortableWalletMaterialDraft.LegacySubstrateSource(
                address, PortableWalletMaterialDraft.SourceType.MNEMONIC, keypair.publicKey.copyOf(), keypair.privateKey.copyOf(),
                null, entropy.copyOf(), seed.copyOf(), mnemonic, path,
            )
        val source =
            PortableWalletMaterialDraft.Snapshot(
                listOf(
                    wallet(
                        id = 7,
                        selected = true,
                        position = 0,
                        substratePublic = keypair.publicKey,
                        substrateAccount = accountId,
                        substrateCrypto = CryptoType.ED25519,
                        legacy = legacy,
                    ),
                ),
            )
        val projected = transcode.toSemanticSnapshot(source)
        try {
            val slot = projected.wallets.single().slots.single()
            assertEquals(role.LEGACY_SUBSTRATE, slot.role)
            assertArrayEquals(keypair.publicKey, slot.value(field.PUBLIC_KEY))
            assertArrayEquals(keypair.privateKey, slot.value(field.PRIVATE_KEY))
            assertTrue(slot.fields.none { it.id == field.NONCE })
            assertArrayEquals(entropy, slot.value(field.ENTROPY))
            assertArrayEquals(seed, slot.value(field.SEED))
            assertEquals(path, slot.value(field.DERIVATION_PATH).toString(Charsets.UTF_8))
            assertEquals(address, slot.value(field.ACCOUNT_ID_OR_ADDRESS).toString(Charsets.UTF_8))
            assertArrayEquals(byteArrayOf(2), slot.value(field.CRYPTO_TYPE))
            assertArrayEquals(byteArrayOf(4), slot.value(field.SOURCE_RECIPE))
            assertEquals(mnemonic, slot.value(field.MNEMONIC).toString(Charsets.UTF_8))
        } finally {
            projected.clearSecrets()
            source.clearSecrets()
            entropy.fill(0)
            seed.fill(0)
        }
    }

    @Test
    fun `invalid root binding chain binding and watch only draft fail closed`() {
        val evmRaw =
            ethereumSecrets(ethereumKeypair = Keypair(byteArrayOf(3, 7), ByteArray(32) { 1 }))
                .toByteArray()
        val wrongRoot =
            PortableWalletMaterialDraft.Snapshot(
                listOf(
                    wallet(
                        id = 7,
                        selected = true,
                        position = 0,
                        ethereumPublic = byteArrayOf(9, 7),
                        ethereumAddress = byteArrayOf(4, 7),
                        ethereumRaw = evmRaw,
                    ),
                ),
            )
        val chainRaw = chainSecrets(Keypair(byteArrayOf(6, 7), ByteArray(32) { 2 })).toByteArray()
        val wrongChain =
            PortableWalletMaterialDraft.Snapshot(
                listOf(
                    wallet(
                        id = 7,
                        selected = true,
                        position = 0,
                        chains =
                            listOf(
                                PortableWalletMaterialDraft.ChainIdentity(
                                    "chain-a", b64(byteArrayOf(9, 7)), b64(byteArrayOf(7, 7)),
                                    CryptoType.ED25519.name, "Wrong", true,
                                ) to chainRaw,
                            ),
                    ),
                ),
            )
        val watchOnly = PortableWalletMaterialDraft.Snapshot(listOf(wallet(id = 7, selected = true, position = 0)))
        try {
            assertThrows(IllegalArgumentException::class.java) { transcode.toSemanticSnapshot(wrongRoot) }
            assertThrows(IllegalArgumentException::class.java) { transcode.toSemanticSnapshot(wrongChain) }
            assertThrows(IllegalArgumentException::class.java) { transcode.toSemanticSnapshot(watchOnly) }
        } finally {
            wrongRoot.clearSecrets()
            wrongChain.clearSecrets()
        }
    }

    private fun wallet(
        id: Long,
        selected: Boolean,
        position: Int,
        substratePublic: ByteArray? = null,
        substrateAccount: ByteArray? = null,
        substrateRaw: ByteArray? = null,
        substrateCrypto: CryptoType = CryptoType.ED25519,
        ethereumPublic: ByteArray? = null,
        ethereumAddress: ByteArray? = null,
        ethereumRaw: ByteArray? = null,
        tonPublic: ByteArray? = null,
        tonRaw: ByteArray? = null,
        chains: List<Pair<PortableWalletMaterialDraft.ChainIdentity, ByteArray>> = emptyList(),
        favorites: List<PortableWalletMaterialDraft.FavoriteIdentity> = emptyList(),
        legacy: PortableWalletMaterialDraft.LegacySubstrateSource? = null,
    ): PortableWalletMaterialDraft.Wallet {
        return PortableWalletMaterialDraft.Wallet(
            PortableWalletMaterialDraft.WalletIdentity(
                id, "Wallet $id", selected, position, true,
                substratePublic?.let(::b64), substratePublic?.let { substrateCrypto.name },
                substrateAccount?.let(::b64), ethereumPublic?.let(::b64), ethereumAddress?.let(::b64),
                tonPublic?.let(::b64), chains.map { it.first }, favorites,
            ),
            substrateRaw,
            ethereumRaw,
            tonRaw,
            chains.map { it.second },
            legacy,
        )
    }

    private fun assertSemanticRoundtrip(snapshot: PortableWalletSemanticMaterial.Snapshot) {
        val encoded = semantic.encode(snapshot)
        var decoded: PortableWalletSemanticMaterial.Snapshot? = null
        var reencoded: ByteArray? = null
        try {
            decoded = semantic.decode(encoded)
            reencoded = semantic.encode(decoded)
            assertArrayEquals(encoded, reencoded)
        } finally {
            reencoded?.fill(0)
            decoded?.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray = fields.single { it.id == id }.value

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
