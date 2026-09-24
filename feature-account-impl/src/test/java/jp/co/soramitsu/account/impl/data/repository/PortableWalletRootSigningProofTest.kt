package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic
import java.util.Base64

class PortableWalletRootSigningProofTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val proof = PortableWalletRootSigningProof
    private val tonPhrase = "cluster notice abandon frost gospel boring element situate click mix vague replace " +
        "imitate garment useful crater resource dose tenant theme foam ancient phrase slight"

    @Test
    fun `decoded ED25519 and EVM roots prove original signing keys and identities`() {
        val substrate = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519,
            ByteArray(32) { 7 },
            emptyList(),
        )
        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { (it + 1).toByte() })
        val snapshot = snapshot(
            listOf(
                substrateSlot(substrate.publicKey, substrate.privateKey),
                evmSlot(evm.publicKey, evm.privateKey),
            )
        )
        val encoded = codec.encode(snapshot)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.wallets)
            assertEquals(1, counts.substrateRoots)
            assertEquals(1, counts.evmRoots)
            assertEquals(0, counts.nativeTonRoots)
            assertEquals(0, counts.unprovenSlots)
            assertEquals(0, counts.unprovenRecoveryFields)
            assertEquals("PortableWalletRootSigningProof.Counts(redacted)", counts.toString())
        } finally {
            snapshot.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `iOS 64-byte ED25519 root proves its miniSeed signer but retains an unproven suffix`() {
        val root = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 7 }, emptyList(),
        )
        // iOS SigningWrapperProtocol.signEd25519 uses Data.miniSeed, the first
        // 32 bytes, even when its Keychain entry is a 64-byte seed.
        val iosSecret = root.privateKey + ByteArray(32) { (it + 33).toByte() }
        val source = snapshot(listOf(substrateSlot(root.publicKey, iosSecret)))
        val encoded = codec.encode(source)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.substrateRoots)
            assertEquals(1, counts.unprovenRecoveryFields)
            assertInvalid(
                slot(
                    role.SUBSTRATE_ROOT, "",
                    bytes(field.PUBLIC_KEY, root.publicKey), bytes(field.PRIVATE_KEY, iosSecret),
                    bytes(field.NONCE, ByteArray(32)),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, root.publicKey),
                    one(field.CRYPTO_TYPE, 2), one(field.SOURCE_RECIPE, 0),
                ),
            )
        } finally {
            source.clearSecrets()
            encoded.fill(0)
            iosSecret.fill(0)
        }

        val different = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 8 }, emptyList(),
        )
        assertInvalid(substrateSlot(root.publicKey, different.privateKey + ByteArray(32) { 9 }))
    }

    @Test
    fun `ECDSA cannot acquire the iOS 64-byte ED25519 secret shape`() {
        val root = SubstrateKeypairFactory.generate(
            EncryptionType.ECDSA, ByteArray(32) { 12 }, emptyList(),
        )
        assertInvalid(
            substrateSlot(
                root.publicKey, root.privateKey + ByteArray(32) { 1 },
                account = root.publicKey.substrateAccountId(), cryptoType = 3,
            ),
        )
    }

    @Test
    fun `a public match cannot hide a different substrate or EVM private key`() {
        val substrate = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 8 }, emptyList(),
        )
        val another = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 9 }, emptyList(),
        )
        assertInvalid(substrateSlot(substrate.publicKey, another.privateKey))

        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 1 })
        val differentEvm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 2 })
        assertInvalid(evmSlot(evm.publicKey, differentEvm.privateKey))
    }

    @Test
    fun `Substrate ECDSA root proves its hashed account identity and local signature`() {
        val keypair = SubstrateKeypairFactory.generate(
            EncryptionType.ECDSA,
            ByteArray(32) { 12 },
            emptyList(),
        )
        val source = snapshot(
            listOf(
                substrateSlot(
                    keypair.publicKey,
                    keypair.privateKey,
                    account = keypair.publicKey.substrateAccountId(),
                    cryptoType = 3,
                ),
            ),
        )
        val encoded = codec.encode(source)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.substrateRoots)
            assertEquals(0, counts.unprovenSlots)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `wrong account identities and an invalid SR25519 nonce fail before a proof`() {
        val substrate = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 10 }, emptyList(),
        )
        assertInvalid(substrateSlot(substrate.publicKey, substrate.privateKey, account = ByteArray(32)))

        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 3 })
        assertInvalid(evmSlot(evm.publicKey, evm.privateKey, address = ByteArray(20)))

        val malformedSr = slot(
            role.SUBSTRATE_ROOT, "",
            bytes(field.PUBLIC_KEY, ByteArray(32) { 1 }),
            bytes(field.PRIVATE_KEY, ByteArray(32) { 2 }),
            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(32) { 1 }),
            one(field.CRYPTO_TYPE, 1),
            one(field.SOURCE_RECIPE, 0),
        )
        assertInvalid(malformedSr)
    }

    @Test
    fun `Android V3 TON phrase and native 32-byte signing seed prove V4R2 identity`() {
        val native = tonNative()
        val source = snapshot(
            listOf(
                tonSlot(
                    native.privateKey, native.publicKey, 1,
                    rawTonAddress(native.publicKey), seedPhrase = tonPhrase,
                ),
            ),
        )
        val encoded = codec.encode(source)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.nativeTonRoots)
            assertEquals(0, counts.unprovenSlots)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
            native.privateKey.fill(0)
        }
    }

    @Test
    fun `BIP39-origin TON phrase remains recoverable under released Android validator semantics`() {
        val phrase = List(11) { "abandon" }.joinToString(" ") + " about"
        val native = PrivateKeyEd25519(Mnemonic.toSeed(phrase.split(' ')))
        val privateKey = native.key.toByteArray()
        val publicKey = native.publicKey().key.toByteArray()
        val source = snapshot(
            listOf(
                tonSlot(
                    privateKey, publicKey, 1, rawTonAddress(publicKey), seedPhrase = phrase,
                ),
            ),
        )
        val encoded = codec.encode(source)
        try {
            assertEquals(1, proof.verify(encoded).nativeTonRoots)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
            privateKey.fill(0)
        }
    }

    @Test
    fun `iOS native TON seed plus public and serialized address prove V4R2 identity`() {
        val native = tonNative()
        val serialized = iosTonAddress(native.publicKey)
        val source = snapshot(
            listOf(
                tonSlot(
                    native.privateKey + native.publicKey, native.publicKey, 2, serialized,
                    iosPhrase = tonPhrase,
                ),
            ),
        )
        val encoded = codec.encode(source)
        try {
            assertEquals(1, proof.verify(encoded).nativeTonRoots)
            assertTrue(encoded.isNotEmpty())
        } finally {
            source.clearSecrets()
            encoded.fill(0)
            native.privateKey.fill(0)
        }
    }

    @Test
    fun `TON rejects private suffix phrase address and ambiguous JSON mutation`() {
        val native = tonNative()
        val raw = rawTonAddress(native.publicKey)
        val wrongSuffix = (native.privateKey + native.publicKey).also { it[it.lastIndex] = 0 }
        assertInvalid(tonSlot(wrongSuffix, native.publicKey, 1, raw, iosPhrase = tonPhrase))
        assertInvalid(tonSlot(native.privateKey, native.publicKey, 1, raw))
        assertInvalid(
            tonSlot(
                native.privateKey, native.publicKey, 1, raw,
                seedPhrase = tonPhrase.replace("cluster", "abandon"),
            ),
        )
        assertInvalid(
            tonSlot(
                native.privateKey, native.publicKey, 1,
                raw.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() },
                seedPhrase = tonPhrase,
            ),
        )
        assertInvalid(
            tonSlot(
                native.privateKey + native.publicKey, native.publicKey, 2,
                iosTonAddress(native.publicKey).decodeToString()
                    .replace("\"workchain\":0", "\"workchain\":1").toByteArray(),
                iosPhrase = tonPhrase,
            ),
        )
        assertInvalid(
            tonSlot(
                native.privateKey + native.publicKey, native.publicKey, 2,
                iosTonAddress(native.publicKey).decodeToString()
                    .replace("\"workchain\":0", "\"workchain\":0,\"workchain\":0")
                    .toByteArray(),
                iosPhrase = tonPhrase,
            ),
        )
        native.privateKey.fill(0)
    }

    @Test
    fun `legacy chain and watch slots remain unproven even beside a valid signed root`() {
        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 4 })
        val source = snapshot(
            listOf(
                evmSlot(evm.publicKey, evm.privateKey),
                slot(
                    role.LEGACY_SUBSTRATE, "",
                    bytes(field.PUBLIC_KEY, ByteArray(32) { 1 }),
                    bytes(field.PRIVATE_KEY, ByteArray(32) { 2 }),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, "5Synthetic".toByteArray()),
                    one(field.CRYPTO_TYPE, 2), one(field.SOURCE_RECIPE, 5),
                ),
                slot(
                    role.CHAIN_ACCOUNT, "chain-x",
                    bytes(field.PUBLIC_KEY, ByteArray(32) { 1 }),
                    bytes(field.PRIVATE_KEY, ByteArray(32) { 2 }),
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(32) { 3 }),
                    one(field.CRYPTO_TYPE, 2), bytes(field.CHAIN_NAME, byteArrayOf()),
                    one(field.INITIALIZED_OR_FAVORITE, 1), one(field.SOURCE_RECIPE, 0),
                ),
                slot(
                    role.WATCH_IDENTITY, "0000",
                    bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 9 }),
                    one(field.WATCH_ECOSYSTEM, 2),
                ),
            )
        )
        val encoded = codec.encode(source)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.evmRoots)
            assertEquals(3, counts.unprovenSlots)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `optional root source fields are counted as unproven even when signer works`() {
        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 5 })
        val root = evmSlot(evm.publicKey, evm.privateKey)
        val fieldsWithUnprovenSources = root.fields + listOf(
            bytes(field.NONCE, ByteArray(32) { 0x37 }),
            bytes(field.ENTROPY, ByteArray(16) { 0x42 }),
        )
        val source = snapshot(
            listOf(
                slot(
                    root.role, root.key,
                    *fieldsWithUnprovenSources.toTypedArray(),
                ),
            ),
        )
        val encoded = codec.encode(source)
        try {
            val counts = proof.verify(encoded)
            assertEquals(1, counts.evmRoots)
            assertEquals(2, counts.unprovenRecoveryFields)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun assertInvalid(slot: PortableWalletSemanticMaterial.Slot) {
        val source = snapshot(listOf(slot))
        val encoded = codec.encode(source)
        try {
            assertThrows(Exception::class.java) { proof.verify(encoded) }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun snapshot(slots: List<PortableWalletSemanticMaterial.Slot>): PortableWalletSemanticMaterial.Snapshot {
        return PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() }, 0, true, "Root proof", emptyList(), slots,
                ),
            ),
        )
    }

    private fun substrateSlot(
        publicKey: ByteArray,
        privateKey: ByteArray,
        account: ByteArray = publicKey,
        cryptoType: Int = 2,
    ) = slot(
        role.SUBSTRATE_ROOT, "",
        bytes(field.PUBLIC_KEY, publicKey), bytes(field.PRIVATE_KEY, privateKey),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, account), one(field.CRYPTO_TYPE, cryptoType),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun evmSlot(
        publicKey: ByteArray,
        privateKey: ByteArray,
        address: ByteArray? = null
    ) = slot(
        role.EVM_ROOT, "",
        bytes(field.PUBLIC_KEY, publicKey), bytes(field.PRIVATE_KEY, privateKey),
        bytes(
            field.ACCOUNT_ID_OR_ADDRESS,
            address ?: publicKey.ethereumAddressFromPublicKey(),
        ),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun tonSlot(
        secret: ByteArray,
        publicKey: ByteArray,
        addressEncoding: Int,
        address: ByteArray,
        seedPhrase: String? = null,
        iosPhrase: String? = null,
    ) = slot(
        role.TON_ROOT, "",
        *listOfNotNull(
            bytes(field.PUBLIC_KEY, publicKey), bytes(field.PRIVATE_KEY, secret),
            seedPhrase?.let { bytes(field.SEED, it.toByteArray()) },
            bytes(field.ACCOUNT_ID_OR_ADDRESS, address), one(field.SOURCE_RECIPE, 0),
            iosPhrase?.let { bytes(field.MNEMONIC, it.toByteArray()) },
            one(field.TON_CONTRACT_VERSION, 2), one(field.TON_ADDRESS_ENCODING, addressEncoding),
        ).toTypedArray(),
    )

    private fun tonNative(): TonPair {
        val native = PrivateKeyEd25519(Mnemonic.toSeed(tonPhrase.split(' ')))
        return TonPair(native.key.toByteArray(), native.publicKey().key.toByteArray())
    }

    private fun rawTonAddress(publicKey: ByteArray): ByteArray =
        byteArrayOf(0) + publicKey.tonAccountId(false).substring(2).chunked(2).map {
            it.toInt(16).toByte()
        }

    private fun iosTonAddress(publicKey: ByteArray): ByteArray {
        val hash = rawTonAddress(publicKey).copyOfRange(1, 33)
        return "{\"workchain\":0,\"hash\":\"${Base64.getEncoder().encodeToString(hash)}\"}"
            .toByteArray()
    }

    private fun slot(
        role: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field
    ) = PortableWalletSemanticMaterial.Slot(role, key, fields.sortedBy { it.id })

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private data class TonPair(val privateKey: ByteArray, val publicKey: ByteArray)
}
