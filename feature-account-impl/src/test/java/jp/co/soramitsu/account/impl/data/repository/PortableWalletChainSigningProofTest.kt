package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletChainSigningProof.ApprovedGenesis
import jp.co.soramitsu.account.impl.data.repository.PortableWalletChainSigningProof.IdentityKind
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableWalletChainSigningProofTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val proof = PortableWalletChainSigningProof
    private val substrateGenesis = "0x" + "01".repeat(32)
    private val ethereumGenesis = "0x" + "02".repeat(32)
    private val policy = listOf(
        ApprovedGenesis(substrateGenesis, IdentityKind.SUBSTRATE),
        ApprovedGenesis(ethereumGenesis, IdentityKind.ETHEREUM),
    )

    @Test
    fun `canonical V2 substrate and Ethereum originals retain exact keys and prove local signing`() {
        val substrate = substrateFixture()
        val ethereum = ethereumFixture()
        val source = snapshot(listOf(substrate, ethereum))
        val encoded = codec.encode(source)
        val before = encoded.copyOf()
        try {
            val result = proof.verify(encoded, policy)
            assertEquals(2, result.wallets)
            assertEquals(2, result.chainAccounts)
            assertEquals(2, result.exactOriginalSources)
            assertEquals("PortableWalletChainSigningProof.Counts(redacted)", result.toString())
            assertEquals("PortableWalletChainSigningProof.ApprovedGenesis(redacted)", policy[0].toString())
            assertArrayEquals(before, encoded)
            val plan = PortableWalletReceiveInstallPlan.decode(encoded)
            try {
                assertEquals(
                    2,
                    plan.blockers.count {
                        it.reason == PortableWalletReceiveInstallPlan.BlockerReason.V2_CHAIN_UNPROVEN
                    },
                )
                assertTrue(
                    plan.blockers.any {
                        it.reason == PortableWalletReceiveInstallPlan.BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE
                    },
                )
            } finally {
                plan.clearSecrets()
            }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
            before.fill(0)
        }
    }

    @Test
    fun `Substrate ECDSA V2 original signs with its approved account ID`() {
        val source = snapshot(listOf(substrateFixture(EncryptionType.ECDSA)))
        val encoded = codec.encode(source)
        try {
            assertEquals(1, proof.verify(encoded, policy).chainAccounts)
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `missing unknown malformed or wrong-kind genesis policy cannot prove a chain`() {
        val source = snapshot(listOf(substrateFixture()))
        val encoded = codec.encode(source)
        try {
            assertThrows(IllegalArgumentException::class.java) { proof.verify(encoded, emptyList()) }
            assertThrows(IllegalArgumentException::class.java) {
                proof.verify(encoded, listOf(ApprovedGenesis(ethereumGenesis, IdentityKind.SUBSTRATE)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                proof.verify(encoded, listOf(ApprovedGenesis(substrateGenesis, IdentityKind.ETHEREUM)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                proof.verify(encoded, listOf(policy[0], policy[0]))
            }
            assertThrows(IllegalArgumentException::class.java) {
                ApprovedGenesis(substrateGenesis.uppercase(), IdentityKind.SUBSTRATE)
            }
            assertThrows(IllegalArgumentException::class.java) {
                ApprovedGenesis("chain-x", IdentityKind.SUBSTRATE)
            }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `different public account or private bytes cannot use an original source as proof`() {
        val fixture = substrateFixture()
        val chain = fixture.slots[0]
        val source = fixture.slots[1]
        val privateField = chain.fields.single { it.id == field.PRIVATE_KEY }
        assertRejected(
            fixture.copy(
                slots = listOf(
                    withField(chain, field.PRIVATE_KEY, privateField.value.copyOf().also { it[0]++ }),
                    source,
                ),
            ),
        )
        assertRejected(
            fixture.copy(
                slots = listOf(
                    withField(chain, field.PUBLIC_KEY, ByteArray(32) { 7 }), source,
                ),
            ),
        )
        val mismatchedAccount = ByteArray(32) { 9 }
        assertRejected(
            fixture.copy(
                slots = listOf(
                    withField(chain, field.ACCOUNT_ID_OR_ADDRESS, mismatchedAccount),
                    withField(source, field.BINDING_ACCOUNT_ID, mismatchedAccount),
                ),
            ),
        )
    }

    @Test
    fun `a missing duplicated or unsupported original V2 source cannot prove export provenance`() {
        val fixture = substrateFixture()
        assertRejected(fixture.copy(slots = fixture.slots.take(1)))
        val duplicated = slot(role.AUXILIARY_SOURCE, "0001", *fixture.slots[1].fields.toTypedArray())
        assertRejected(fixture.copy(slots = fixture.slots + duplicated))
        val iosShaped = slot(
            role.AUXILIARY_SOURCE, "0000",
            one(field.SOURCE_RECIPE, 0), one(field.SOURCE_PLATFORM, 2),
            one(field.SOURCE_SLOT_ROLE, 1), one(field.BINDING_KIND, 5),
            bytes(field.BINDING_CHAIN_ID, substrateGenesis.toByteArray()),
            one(field.SOURCE_FORMAT, 1), bytes(field.SOURCE_BYTES, fixture.originalBytes),
            bytes(field.BINDING_ACCOUNT_ID, fixture.accountId),
        )
        assertRejected(fixture.copy(slots = listOf(fixture.slots[0], iosShaped)))
        assertRejected(
            fixture.copy(
                slots = listOf(
                    fixture.slots[0],
                    withField(fixture.slots[1], field.SOURCE_BYTES, fixture.originalBytes + byteArrayOf(0)),
                ),
            ),
        )
    }

    @Test
    fun `missing or altered recovery fields cannot be silently normalized from exact V2 original`() {
        val fixture = substrateFixture()
        val chain = fixture.slots[0]
        val source = fixture.slots[1]
        assertRejected(
            fixture.copy(
                slots = listOf(
                    slot(role.CHAIN_ACCOUNT, substrateGenesis, *chain.fields.filter { it.id != field.SEED }.toTypedArray()),
                    source,
                ),
            ),
        )
        assertRejected(
            fixture.copy(
                slots = listOf(withField(chain, field.SEED, ByteArray(32) { 5 }), source),
            ),
        )
    }

    private fun assertRejected(vararg fixtures: Fixture) {
        val source = snapshot(fixtures.toList())
        val encoded = codec.encode(source)
        try {
            assertThrows(Exception::class.java) { proof.verify(encoded, policy) }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun substrateFixture(encryption: EncryptionType = EncryptionType.ED25519): Fixture {
        require(encryption == EncryptionType.ED25519 || encryption == EncryptionType.ECDSA)
        val seed = ByteArray(32) { (if (encryption == EncryptionType.ECDSA) 4 else 3).toByte() }
        val pair = SubstrateKeypairFactory.generate(encryption, seed, emptyList())
        val accountId = pair.publicKey.substrateAccountId()
        val raw = ChainAccountSecrets(pair, seed = seed, derivationPath = "").toHexString().hexBytes()
        return Fixture(
            accountId,
            raw,
            listOf(
                chainSlot(
                    substrateGenesis, pair.publicKey, pair.privateKey, accountId,
                    if (encryption == EncryptionType.ECDSA) 3 else 2, seed, "",
                ),
                originalSlot(substrateGenesis, accountId, raw),
            ),
        )
    }

    private fun ethereumFixture(): Fixture {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val pair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val accountId = pair.publicKey.ethereumAddressFromPublicKey()
        val path = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
        val raw = ChainAccountSecrets(pair, seed = privateKey, derivationPath = path).toHexString().hexBytes()
        return Fixture(
            accountId,
            raw,
            listOf(
                chainSlot(ethereumGenesis, pair.publicKey, privateKey, accountId, 3, privateKey, path),
                originalSlot(ethereumGenesis, accountId, raw),
            ),
        )
    }

    private fun chainSlot(
        genesis: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
        accountId: ByteArray,
        cryptoType: Int,
        seed: ByteArray,
        path: String,
    ) = slot(
        role.CHAIN_ACCOUNT, genesis,
        bytes(field.PUBLIC_KEY, publicKey), bytes(field.PRIVATE_KEY, privateKey),
        bytes(field.SEED, seed), bytes(field.DERIVATION_PATH, path.toByteArray()),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, accountId), one(field.CRYPTO_TYPE, cryptoType),
        bytes(field.CHAIN_NAME, byteArrayOf()), one(field.INITIALIZED_OR_FAVORITE, 1),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun originalSlot(
        genesis: String,
        accountId: ByteArray,
        raw: ByteArray
    ) = slot(
        role.AUXILIARY_SOURCE, "0000",
        one(field.SOURCE_RECIPE, 0), one(field.SOURCE_PLATFORM, 1),
        one(field.SOURCE_SLOT_ROLE, 14), one(field.BINDING_KIND, 5),
        bytes(field.BINDING_CHAIN_ID, genesis.toByteArray()),
        one(field.SOURCE_FORMAT, 3), bytes(field.SOURCE_BYTES, raw),
        bytes(field.BINDING_ACCOUNT_ID, accountId),
    )

    private fun snapshot(fixtures: List<Fixture>) = PortableWalletSemanticMaterial.Snapshot(
        0,
        fixtures.mapIndexed { index, fixture ->
            PortableWalletSemanticMaterial.Wallet(
                ByteArray(16) { (it + 1 + index * 16).toByte() },
                index.toLong(), true, "Chain $index", emptyList(),
                listOf(rootSlot(index)) + fixture.slots.map { original ->
                    slot(
                        original.role,
                        original.key,
                        *original.fields.map { bytes(it.id, it.value) }.toTypedArray(),
                    )
                },
            )
        },
    )

    private fun rootSlot(index: Int): PortableWalletSemanticMaterial.Slot {
        val pair = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { (index + 17).toByte() }, emptyList(),
        )
        return slot(
            role.SUBSTRATE_ROOT, "",
            bytes(field.PUBLIC_KEY, pair.publicKey), bytes(field.PRIVATE_KEY, pair.privateKey),
            bytes(field.ACCOUNT_ID_OR_ADDRESS, pair.publicKey),
            one(field.CRYPTO_TYPE, 2), one(field.SOURCE_RECIPE, 0),
        )
    }

    private fun withField(
        slot: PortableWalletSemanticMaterial.Slot,
        id: Int,
        value: ByteArray,
    ) = slot(
        slot.role, slot.key,
        *slot.fields.map {
            if (it.id == id) bytes(id, value) else bytes(it.id, it.value)
        }.toTypedArray(),
    )

    private fun slot(
        role: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ) = PortableWalletSemanticMaterial.Slot(role, key, fields.sortedBy { it.id })

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))

    private fun String.hexBytes(): ByteArray = removePrefix("0x").chunked(2).map {
        it.toInt(16).toByte()
    }.toByteArray()

    private data class Fixture(
        val accountId: ByteArray,
        val originalBytes: ByteArray,
        val slots: List<PortableWalletSemanticMaterial.Slot>,
    )
}
