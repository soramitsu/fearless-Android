package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletReceiveInstallPlan.BlockerReason
import jp.co.soramitsu.account.impl.data.repository.PortableWalletReceiveInstallPlan.Destination
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableWalletReceiveInstallPlanTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val receiving = PortableWalletReceiveInstallPlan

    @Test
    fun `every source intent and presentation field survives an owned round trip`() {
        val source = mixedSnapshot()
        val encoded = codec.encode(source)
        val expected = encoded.copyOf()
        val plan = receiving.decode(encoded)
        try {
            source.clearSecrets()
            encoded.fill(0)
            assertRoundTrip(plan, expected)
            assertEquals(1, plan.selectedIndex)
            assertEquals(0xffff_ffffL, plan.wallets[0].sourcePosition)
            assertEquals(
                listOf(
                    Destination.V3_SUBSTRATE,
                    Destination.V1_LEGACY_SOURCE,
                    Destination.V2_CHAIN,
                    Destination.FAVORITE_CHAIN,
                    Destination.ORIGINAL_SOURCE,
                    Destination.ORIGINAL_SOURCE,
                    Destination.ORIGINAL_SOURCE,
                    Destination.WATCH_IDENTITY,
                ),
                plan.wallets[0].materials.map { it.destination },
            )
            val originals = plan.wallets[0].materials.filter { it.destination == Destination.ORIGINAL_SOURCE }
            assertArrayEquals(byteArrayOf(0x61), originals[0].fieldValueCopy(field.SOURCE_BYTES))
            assertArrayEquals(byteArrayOf(0x62), originals[1].fieldValueCopy(field.SOURCE_BYTES))
            assertArrayEquals(byteArrayOf(0x63), originals[2].fieldValueCopy(field.SOURCE_BYTES))
            assertArrayEquals("USD".toByteArray(), plan.wallets[0].metadata.single().valueCopy())
            assertEquals(2, plan.wallets.size)
            assertEquals("PortableWalletReceiveInstallPlan.Plan(redacted)", plan.toString())
            assertEquals(
                "PortableWalletReceiveInstallPlan.MaterialIntent(redacted)",
                plan.wallets[0].materials.first().toString(),
            )
        } finally {
            plan.clearSecrets()
            source.clearSecrets()
            encoded.fill(0)
            expected.fill(0)
        }
    }

    @Test
    fun `caller and snapshot mutation cannot change plan bytes and clear erases owned copies`() {
        val source = mixedSnapshot()
        val encoded = codec.encode(source)
        val plan = receiving.decode(encoded)
        val root = plan.wallets[0].materials.first()
        val privateField = root.fields.single { it.id == field.PRIVATE_KEY }
        val expectedPrivate = privateField.valueCopy()
        try {
            source.clearSecrets()
            encoded.fill(0)
            assertThrows(UnsupportedOperationException::class.java) {
                (plan.wallets as MutableList<*>).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (root.fields as MutableList<*>).clear()
            }
            val external = plan.snapshotCopy()
            try {
                external.wallets[0].slots.first().fields.single { it.id == field.PRIVATE_KEY }.value.fill(0)
                assertArrayEquals(expectedPrivate, root.fieldValueCopy(field.PRIVATE_KEY))
            } finally {
                external.clearSecrets()
            }
            plan.clearSecrets()
            assertTrue(privateField.valueCopy().all { it == 0.toByte() })
            assertTrue(plan.wallets[0].portableIdCopy().all { it == 0.toByte() })
            assertTrue(plan.wallets[0].metadata.single().valueCopy().all { it == 0.toByte() })
            assertThrows(IllegalStateException::class.java) { plan.snapshotCopy() }
            plan.clearSecrets()
        } finally {
            plan.clearSecrets()
            source.clearSecrets()
            encoded.fill(0)
            expectedPrivate.fill(0)
        }
    }

    @Test
    fun `all unproven roles and sources have explicit blockers`() {
        val source = mixedSnapshot()
        val encoded = codec.encode(source)
        val plan = receiving.decode(encoded)
        try {
            val reasons = plan.blockers.map { it.reason }
            assertTrue(BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE in reasons)
            assertTrue(BlockerReason.V1_SOURCE_UNPROVEN in reasons)
            assertTrue(BlockerReason.V2_CHAIN_UNPROVEN in reasons)
            assertTrue(BlockerReason.FAVORITE_CHAIN_UNMAPPED in reasons)
            assertTrue(BlockerReason.ORIGINAL_SOURCE_UNPROVEN in reasons)
            assertTrue(BlockerReason.WATCH_IDENTITY_UNPROVEN in reasons)
            assertTrue(BlockerReason.METADATA_UNMAPPED in reasons)
            assertEquals(3, reasons.count { it == BlockerReason.ORIGINAL_SOURCE_UNPROVEN })
            assertFalse(BlockerReason.ROOT_RECOVERY_UNPROVEN in reasons)
            assertEquals(
                setOf("0000", "0001", "0002"),
                plan.blockers.filter { it.reason == BlockerReason.ORIGINAL_SOURCE_UNPROVEN }
                    .mapNotNull { it.slotKey }.toSet(),
            )
        } finally {
            plan.clearSecrets()
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `root only and iOS ED64 material remain blocked`() {
        val keypair = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519,
            ByteArray(32) { 9 },
            emptyList(),
        )
        val rootOnly = snapshot(listOf(substrateRoot(keypair.publicKey, keypair.privateKey)))
        val ios = snapshot(
            listOf(substrateRoot(keypair.publicKey, keypair.privateKey + ByteArray(32) { 7 })),
        )
        assertBlockedRoot(rootOnly, false)
        assertBlockedRoot(ios, true)
    }

    @Test
    fun `V1 only source is retained but cannot claim a proved root`() {
        val source = snapshot(listOf(legacySource()))
        val encoded = codec.encode(source)
        val plan = receiving.decode(encoded)
        try {
            assertEquals(Destination.V1_LEGACY_SOURCE, plan.wallets.single().materials.single().destination)
            assertTrue(plan.blockers.any { it.reason == BlockerReason.V1_SOURCE_UNPROVEN })
            assertTrue(plan.blockers.any { it.reason == BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE })
        } finally {
            plan.clearSecrets()
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun assertBlockedRoot(source: PortableWalletSemanticMaterial.Snapshot, recoveryUnproven: Boolean) {
        val encoded = codec.encode(source)
        val plan = receiving.decode(encoded)
        try {
            assertTrue(plan.blockers.any { it.reason == BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE })
            assertEquals(
                recoveryUnproven,
                plan.blockers.any { it.reason == BlockerReason.ROOT_RECOVERY_UNPROVEN },
            )
        } finally {
            plan.clearSecrets()
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    private fun assertRoundTrip(plan: PortableWalletReceiveInstallPlan.Plan, expected: ByteArray) {
        val copied = plan.snapshotCopy()
        var roundTrip: ByteArray? = null
        try {
            roundTrip = codec.encode(copied)
            assertArrayEquals(expected, roundTrip)
        } finally {
            roundTrip?.fill(0)
            copied.clearSecrets()
        }
    }

    private fun mixedSnapshot(): PortableWalletSemanticMaterial.Snapshot {
        val first = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList(),
        )
        val second = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 2 }, emptyList(),
        )
        return PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() },
                    0xffff_ffffL,
                    true,
                    "First",
                    listOf(PortableWalletSemanticMaterial.Metadata(3, "USD".toByteArray())),
                    listOf(
                        substrateRoot(first.publicKey, first.privateKey),
                        legacySource(),
                        chainSource(),
                        slot(role.FAVORITE_CHAIN, "chain-x", one(field.INITIALIZED_OR_FAVORITE, 1)),
                        auxiliary("0000", 1, 11, 2, 4, 0x61),
                        auxiliary("0001", 1, 14, 5, 3, 0x62, "chain-x", 0x20),
                        auxiliary("0002", 2, 1, 2, 1, 0x63),
                        slot(
                            role.WATCH_IDENTITY,
                            "0000",
                            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 3 }),
                            one(field.WATCH_ECOSYSTEM, 2),
                        ),
                    ),
                ),
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 17).toByte() },
                    4,
                    false,
                    "Second",
                    emptyList(),
                    listOf(substrateRoot(second.publicKey, second.privateKey)),
                ),
            ),
        )
    }

    private fun snapshot(slots: List<PortableWalletSemanticMaterial.Slot>): PortableWalletSemanticMaterial.Snapshot {
        return PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() },
                    0,
                    true,
                    "One",
                    emptyList(),
                    slots,
                ),
            ),
        )
    }

    private fun substrateRoot(publicKey: ByteArray, privateKey: ByteArray) = slot(
        role.SUBSTRATE_ROOT,
        "",
        bytes(field.PUBLIC_KEY, publicKey),
        bytes(field.PRIVATE_KEY, privateKey),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
        one(field.CRYPTO_TYPE, 2),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun legacySource() = slot(
        role.LEGACY_SUBSTRATE,
        "",
        bytes(field.PUBLIC_KEY, ByteArray(32) { 4 }),
        bytes(field.PRIVATE_KEY, ByteArray(32) { 5 }),
        bytes(field.ENTROPY, ByteArray(16) { 8 }),
        bytes(field.DERIVATION_PATH, "//hard".toByteArray()),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, "5Synthetic".toByteArray()),
        one(field.CRYPTO_TYPE, 2),
        one(field.SOURCE_RECIPE, 4),
        bytes(field.MNEMONIC, "abandon test fixture".toByteArray()),
    )

    private fun chainSource() = slot(
        role.CHAIN_ACCOUNT,
        "chain-x",
        bytes(field.PUBLIC_KEY, ByteArray(32) { 6 }),
        bytes(field.PRIVATE_KEY, ByteArray(32) { 7 }),
        bytes(field.SEED, ByteArray(32) { 9 }),
        bytes(field.DERIVATION_PATH, "//chain".toByteArray()),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, byteArrayOf(0x20)),
        one(field.CRYPTO_TYPE, 2),
        bytes(field.CHAIN_NAME, "Chain X".toByteArray()),
        one(field.INITIALIZED_OR_FAVORITE, 1),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun auxiliary(
        key: String,
        platform: Int,
        sourceRole: Int,
        binding: Int,
        format: Int,
        raw: Int,
        chainId: String? = null,
        accountId: Int? = null,
    ) = slot(
        role.AUXILIARY_SOURCE,
        key,
        *listOfNotNull(
            one(field.SOURCE_RECIPE, 0),
            one(field.SOURCE_PLATFORM, platform),
            one(field.SOURCE_SLOT_ROLE, sourceRole),
            one(field.BINDING_KIND, binding),
            chainId?.let { bytes(field.BINDING_CHAIN_ID, it.toByteArray()) },
            one(field.SOURCE_FORMAT, format),
            bytes(field.SOURCE_BYTES, byteArrayOf(raw.toByte())),
            accountId?.let { bytes(field.BINDING_ACCOUNT_ID, byteArrayOf(it.toByte())) },
        ).toTypedArray(),
    )

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ) = PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())
}
