package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletCohortAfterImage.Kind
import jp.co.soramitsu.account.impl.data.repository.PortableWalletReceiveInstallPlan.BlockerReason
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.tonAccountId
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

class PortableWalletCohortAfterImageTest {
    private val cohort = PortableWalletCohortAfterImage
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `canonical cohort pins IDs destinations and every exact source byte`() {
        val source = snapshot()
        val semantic = codec.encode(source)
        val expectedSemantic = semantic.copyOf()
        val address = source.wallets[0].slots.single { it.role == role.LEGACY_SUBSTRATE }
            .value(field.ACCOUNT_ID_OR_ADDRESS).decodeToString()
        val record = cohort.create(semantic, listOf(41L, 42L))
        try {
            source.clearSecrets()
            semantic.fill(0)
            assertSemantic(record, expectedSemantic)
            assertArrayEquals(longArrayOf(41L, 42L), record.localMetaIdsCopy())
            assertEquals(1, record.selectedIndex)
            assertEquals(
                listOf(
                    Kind.WALLET_ROW,
                    Kind.METADATA,
                    Kind.V3_SUBSTRATE,
                    Kind.V1_LEGACY_SOURCE,
                    Kind.V2_CHAIN,
                    Kind.FAVORITE_CHAIN,
                    Kind.ORIGINAL_SOURCE,
                    Kind.ORIGINAL_SOURCE,
                    Kind.WATCH_IDENTITY,
                    Kind.WALLET_ROW,
                    Kind.V3_SUBSTRATE,
                    Kind.SELECTION,
                ),
                record.destinations.map { it.kind },
            )
            assertEquals(42L, record.destinations.last().localMetaId)
            assertEquals(
                "41:SUBSTRATE_SECRETS",
                record.destinations.single { it.kind == Kind.V3_SUBSTRATE && it.walletIndex == 0 }
                    .candidateSecretKey,
            )
            assertEquals(
                "security_source_$address",
                record.destinations.single { it.kind == Kind.V1_LEGACY_SOURCE }.candidateSecretKey,
            )
            assertEquals(
                "41:${"20".repeat(32)}:ACCESS_SECRETS",
                record.destinations.single { it.kind == Kind.V2_CHAIN }.candidateSecretKey,
            )
            val original = record.destinations.single {
                it.kind == Kind.ORIGINAL_SOURCE && it.slotKey == "0000"
            }
            assertNull(original.candidateSecretKey)
            assertTrue(record.blockers.any { it.reason == BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE })
            assertTrue(record.blockers.any { it.reason == BlockerReason.V1_SOURCE_UNPROVEN })
            assertTrue(record.blockers.any { it.reason == BlockerReason.V2_CHAIN_UNPROVEN })
            assertEquals("PortableWalletCohortAfterImage.Record(redacted)", record.toString())
            assertEquals(
                "PortableWalletReceiveInstallPlan.Blocker(redacted)",
                record.blockers.single { it.reason == BlockerReason.V2_CHAIN_UNPROVEN }.toString(),
            )
            assertEquals(
                "PortableWalletCohortAfterImage.Destination(redacted)",
                record.destinations.single { it.kind == Kind.V1_LEGACY_SOURCE }.toString(),
            )

            assertReplay(record, expectedSemantic)
        } finally {
            record.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
            expectedSemantic.fill(0)
        }
    }

    @Test
    fun `pure storage projection retains multiwallet rows metadata V1 V2 and opaque source bytes`() {
        val source = snapshot()
        val semantic = codec.encode(source)
        val afterImage = cohort.create(semantic, listOf(41L, 42L))
        val projection = PortableWalletCohortStorageProjection.project(afterImage)
        try {
            source.clearSecrets()
            semantic.fill(0)
            assertEquals(listOf(41L, 42L), projection.wallets.map { it.localMetaId })
            assertEquals(listOf(false, true), projection.wallets.map { it.selected })
            assertEquals(listOf(0, 1), projection.wallets.map { it.freshInstallRoomPosition })
            assertEquals(0xffff_ffffL, projection.wallets.first().sourcePosition)
            assertEquals(
                PortableWalletCohortStorageProjection.Custody.SIGNED,
                projection.wallets.first().custody
            )
            assertEquals(2, projection.wallets.first().substrateCryptoTypeCode)
            assertEquals("First", projection.wallets.first().name)
            assertArrayEquals("JPY".toByteArray(), projection.wallets.first().metadata.single().valueCopy())
            assertEquals("chain-x", projection.wallets.first().chains.single().chainId)
            assertEquals(2, projection.wallets.first().chains.single().cryptoTypeCode)
            assertEquals(true, projection.wallets.first().favorites.single().isFavorite)
            assertEquals(1, projection.wallets.first().watchIdentities.size)
            assertEquals(
                listOf(Kind.V3_SUBSTRATE, Kind.V1_LEGACY_SOURCE, Kind.V2_CHAIN, Kind.V3_SUBSTRATE),
                projection.secrets.map { it.destination },
            )
            val legacy = projection.secrets.single { it.destination == Kind.V1_LEGACY_SOURCE }
            assertEquals("security_source_" + sourceAddress(), legacy.destinationKey)
            assertArrayEquals(ByteArray(32) { 5 }, legacy.fieldCopy(field.PRIVATE_KEY))
            assertArrayEquals(byteArrayOf(0x22, 0x33), projection.originals.first().sourceBytesCopy())
            assertArrayEquals(byteArrayOf(0x44, 0x55), projection.originals.last().sourceBytesCopy())
            assertTrue(projection.blockers.any { it.reason == BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE })
            assertEquals("PortableWalletCohortStorageProjection.Projection(redacted)", projection.toString())
            val altered = requireNotNull(legacy.fieldCopy(field.PRIVATE_KEY))
            altered.fill(0)
            assertArrayEquals(ByteArray(32) { 5 }, legacy.fieldCopy(field.PRIVATE_KEY))
        } finally {
            projection.clearSecrets()
            afterImage.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
        }
        assertTrue(projection.secrets.first().fieldCopy(field.PRIVATE_KEY)!!.all { it == 0.toByte() })
    }

    @Test
    fun `fresh install positions follow canonical list order through uint32 extremes and ties`() {
        val policy = PortableWalletCohortStorageProjection.FreshInstallPositionPolicy
        val sourcePositions = listOf(0xffff_ffffL, 0L, 0L, 0x7fff_ffffL, 0xffff_ffffL)
        val expected = listOf(0, 1, 2, 3, 4)
        assertEquals(expected, policy.project(sourcePositions))
        assertEquals(expected, policy.project(sourcePositions))
        assertEquals((0 until 128).toList(), policy.project(List(128) { 0L }))
        assertThrows(IllegalArgumentException::class.java) { policy.project(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { policy.project(listOf(-1L)) }
        assertThrows(IllegalArgumentException::class.java) { policy.project(listOf(0x1_0000_0000L)) }
        assertThrows(IllegalArgumentException::class.java) { policy.project(List(129) { 0L }) }
    }

    @Test
    fun `standalone EVM and native TON keys remain exact separate receiving intents`() {
        val evm = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { (it + 1).toByte() })
        val phrase = List(11) { "abandon" }.joinToString(" ") + " about"
        val ton = PrivateKeyEd25519(Mnemonic.toSeed(phrase.split(' ')))
        val tonPublic = ton.publicKey().key.toByteArray()
        val tonPrivate = ton.key.toByteArray()
        val rawTonAddress = byteArrayOf(0) + tonPublic.tonAccountId(false).substring(2).chunked(2)
            .map { it.toInt(16).toByte() }
        val source = PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
            PortableWalletSemanticMaterial.Wallet(
                ByteArray(16) { (it + 1).toByte() }, 8, true, "EVM only", emptyList(),
                    listOf(
                    slot(
                        role.EVM_ROOT, "",
                        bytes(field.PUBLIC_KEY, evm.publicKey), bytes(field.PRIVATE_KEY, evm.privateKey),
                        bytes(field.ACCOUNT_ID_OR_ADDRESS, evm.publicKey.ethereumAddressFromPublicKey()),
                        one(field.SOURCE_RECIPE, 0)
                    ),
                    auxiliaryWithBinding("0000", 3, 12, byteArrayOf(0x31, 0x32)),
                ),
            ),
            PortableWalletSemanticMaterial.Wallet(
                ByteArray(16) { (it + 17).toByte() }, 9, true, "TON only", emptyList(),
                    listOf(
                    slot(
                        role.TON_ROOT, "",
                        bytes(field.PUBLIC_KEY, tonPublic), bytes(field.PRIVATE_KEY, tonPrivate),
                        bytes(field.SEED, phrase.toByteArray()), bytes(field.ACCOUNT_ID_OR_ADDRESS, rawTonAddress),
                        one(field.SOURCE_RECIPE, 0), one(field.TON_CONTRACT_VERSION, 2),
                        one(field.TON_ADDRESS_ENCODING, 1)
                    ),
                    auxiliaryWithBinding("0000", 4, 13, byteArrayOf(0x41, 0x42)),
                ),
            ),
        )
        )
        val semantic = codec.encode(source)
        val afterImage = cohort.create(semantic, listOf(51L, 52L))
        val projection = PortableWalletCohortStorageProjection.project(afterImage)
        try {
            source.clearSecrets()
            semantic.fill(0)
            assertEquals(listOf(Kind.V3_EVM, Kind.V3_TON), projection.secrets.map { it.destination })
            assertEquals(
                listOf("51:ETHEREUM_SECRETS", "52:TON_SECRETS"),
                projection.secrets.map { it.destinationKey }
            )
            assertArrayEquals(evm.privateKey, projection.secrets.first().fieldCopy(field.PRIVATE_KEY))
            assertArrayEquals(tonPrivate, projection.secrets.last().fieldCopy(field.PRIVATE_KEY))
            assertArrayEquals(phrase.toByteArray(), projection.secrets.last().fieldCopy(field.SEED))
            assertArrayEquals(rawTonAddress, projection.secrets.last().fieldCopy(field.ACCOUNT_ID_OR_ADDRESS))
            assertArrayEquals(byteArrayOf(0x31, 0x32), projection.originals.first().sourceBytesCopy())
            assertArrayEquals(byteArrayOf(0x41, 0x42), projection.originals.last().sourceBytesCopy())
            assertEquals(true, projection.wallets.last().selected)
            assertEquals(null, projection.wallets.first().substratePublicKeyCopy())
            assertEquals(null, projection.wallets.last().ethereumAddressCopy())
        } finally {
            projection.clearSecrets()
            afterImage.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
            tonPrivate.fill(0)
        }
    }

    @Test
    fun `projection rejects repeated public wallet identity even under fresh local IDs`() {
        val pair = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 9 }, emptyList())
        val source = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
            PortableWalletSemanticMaterial.Wallet(
                ByteArray(16) { 1 }, 0, true, "one", emptyList(),
                listOf(root(pair.publicKey, pair.privateKey))
            ),
            PortableWalletSemanticMaterial.Wallet(
                ByteArray(16) { 2 }, 1, true, "two", emptyList(),
                listOf(root(pair.publicKey, pair.privateKey))
            ),
        )
        )
        val semantic = codec.encode(source)
        val afterImage = cohort.create(semantic, listOf(61L, 62L))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletCohortStorageProjection.project(afterImage)
            }
        } finally {
            afterImage.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    @Test
    fun `projection rejects repeated chain account identity across wallets`() {
        val source = snapshot()
        val first = source.wallets.first()
        val second = source.wallets.last()
        val chain = first.slots.single { it.role == role.CHAIN_ACCOUNT }
        val repeated = PortableWalletSemanticMaterial.Snapshot(
            source.selectedIndex,
            listOf(
            first,
            PortableWalletSemanticMaterial.Wallet(
                second.portableId, second.sourcePosition,
                second.initialized, second.name, second.metadata, second.slots + chain
            ),
        )
        )
        val semantic = codec.encode(repeated)
        val afterImage = cohort.create(semantic, listOf(71L, 72L))
        try {
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletCohortStorageProjection.project(afterImage)
            }
        } finally {
            afterImage.clearSecrets()
            repeated.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    @Test
    fun `caller mutation cannot change after image and clear erases its copy`() {
        val source = snapshot()
        val semantic = codec.encode(source)
        val record = cohort.create(semantic, listOf(41L, 42L))
        try {
            source.clearSecrets()
            semantic.fill(0)
            assertCopyIsIndependent(record)
            assertThrows(UnsupportedOperationException::class.java) {
                (record.destinations as MutableList<*>).clear()
            }
            record.clearSecrets()
            assertTrue(record.localMetaIdsCopy().all { it == 0L })
            assertThrows(IllegalStateException::class.java) { record.semanticCopy() }
            record.clearSecrets()
        } finally {
            record.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    @Test
    fun `IDs malformed wire and unsupported chain identity fail closed`() {
        val source = snapshot()
        val semantic = codec.encode(source)
        try {
            assertThrows(IllegalArgumentException::class.java) { cohort.create(semantic, listOf(41L)) }
            assertThrows(IllegalArgumentException::class.java) { cohort.create(semantic, listOf(41L, 41L)) }
            assertThrows(IllegalArgumentException::class.java) { cohort.create(semantic, listOf(0L, 42L)) }

            val record = cohort.create(semantic, listOf(41L, 42L))
            assertMalformedWire(record)
            record.clearSecrets()
        } finally {
            source.clearSecrets()
            semantic.fill(0)
        }

        val unsupported = snapshot(chainAccountId = byteArrayOf(0x20))
        val unsupportedBytes = codec.encode(unsupported)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                cohort.create(unsupportedBytes, listOf(41L, 42L))
            }
        } finally {
            unsupported.clearSecrets()
            unsupportedBytes.fill(0)
        }
    }

    @Test
    fun `duplicate global V1 destination across wallets is rejected`() {
        val source = snapshot(duplicateLegacy = true)
        val semantic = codec.encode(source)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                cohort.create(semantic, listOf(41L, 42L))
            }
        } finally {
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    @Test
    fun `V1 address must identify its public source before it becomes a candidate key`() {
        val source = snapshot(mismatchedLegacyAddress = true)
        val semantic = codec.encode(source)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                cohort.create(semantic, listOf(41L, 42L))
            }
        } finally {
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    @Test
    fun `internal construction cannot encode a fabricated destination or readiness`() {
        val source = snapshot()
        val semantic = codec.encode(source)
        val forged = PortableWalletCohortAfterImage.Record(
            longArrayOf(41L, 42L),
            semantic,
            0,
            emptyList(),
            emptyList(),
        )
        try {
            assertThrows(IllegalArgumentException::class.java) { cohort.encode(forged) }
            assertThrows(IllegalArgumentException::class.java) {
                PortableWalletCohortStorageProjection.project(forged)
            }
        } finally {
            forged.clearSecrets()
            source.clearSecrets()
            semantic.fill(0)
        }
    }

    private fun assertSemantic(record: PortableWalletCohortAfterImage.Record, expected: ByteArray) {
        val copy = record.semanticCopy()
        try {
            assertArrayEquals(expected, copy)
        } finally {
            copy.fill(0)
        }
    }

    private fun assertReplay(record: PortableWalletCohortAfterImage.Record, expected: ByteArray) {
        val encoded = cohort.encode(record)
        var replayed: PortableWalletCohortAfterImage.Record? = null
        var replayedEncoded: ByteArray? = null
        try {
            val decoded = cohort.decode(encoded)
            replayed = decoded
            replayedEncoded = cohort.encode(decoded)
            assertArrayEquals(encoded, replayedEncoded)
            assertSemantic(decoded, expected)
            assertEquals(record.destinations, decoded.destinations)
        } finally {
            replayed?.clearSecrets()
            replayedEncoded?.fill(0)
            encoded.fill(0)
        }
    }

    private fun assertCopyIsIndependent(record: PortableWalletCohortAfterImage.Record) {
        val leakedCopy = record.semanticCopy()
        var surviving: ByteArray? = null
        try {
            leakedCopy.fill(0)
            surviving = record.semanticCopy()
            assertTrue(surviving.any { it != 0.toByte() })
        } finally {
            leakedCopy.fill(0)
            surviving?.fill(0)
        }
    }

    private fun assertMalformedWire(record: PortableWalletCohortAfterImage.Record) {
        val encoded = cohort.encode(record)
        val badVersion = encoded.copyOf().also { it[8] = 2 }
        val duplicateIds = encoded.copyOf().also { candidate ->
            encoded.copyInto(candidate, destinationOffset = 19, startIndex = 11, endIndex = 19)
        }
        val trailing = encoded + 0.toByte()
        try {
            assertThrows(IllegalArgumentException::class.java) { cohort.decode(badVersion) }
            assertThrows(IllegalArgumentException::class.java) { cohort.decode(duplicateIds) }
            assertThrows(IllegalArgumentException::class.java) { cohort.decode(trailing) }
        } finally {
            badVersion.fill(0)
            duplicateIds.fill(0)
            trailing.fill(0)
            encoded.fill(0)
        }
    }

    private fun snapshot(
        chainAccountId: ByteArray = ByteArray(32) { 0x20 },
        duplicateLegacy: Boolean = false,
        mismatchedLegacyAddress: Boolean = false,
    ): PortableWalletSemanticMaterial.Snapshot {
        val first = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList(),
        )
        val second = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 2 }, emptyList(),
        )
        val legacyAddress = (if (mismatchedLegacyAddress) second.publicKey else first.publicKey).toAddress(42)
        val firstSlots = listOf(
            root(first.publicKey, first.privateKey),
            legacy(legacyAddress, first.publicKey),
            chain(chainAccountId),
            slot(role.FAVORITE_CHAIN, "chain-x", one(field.INITIALIZED_OR_FAVORITE, 1)),
            auxiliary("0000", 1, 11, 4, byteArrayOf(0x22, 0x33)),
            auxiliary("0001", 2, 1, 1, byteArrayOf(0x44, 0x55)),
            slot(
                role.WATCH_IDENTITY,
                "0000",
                bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 3 }),
                one(field.WATCH_ECOSYSTEM, 2),
            ),
        )
        val secondSlots = listOfNotNull(
            root(second.publicKey, second.privateKey),
            legacy(legacyAddress, first.publicKey).takeIf { duplicateLegacy },
        )
        return PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 1).toByte() },
                    0xffff_ffffL,
                    true,
                    "First",
                    listOf(PortableWalletSemanticMaterial.Metadata(3, "JPY".toByteArray())),
                    firstSlots,
                ),
                PortableWalletSemanticMaterial.Wallet(
                    ByteArray(16) { (it + 17).toByte() },
                    4,
                    false,
                    "Second",
                    emptyList(),
                    secondSlots,
                ),
            ),
        )
    }

    private fun root(publicKey: ByteArray, privateKey: ByteArray) = slot(
        role.SUBSTRATE_ROOT,
        "",
        bytes(field.PUBLIC_KEY, publicKey),
        bytes(field.PRIVATE_KEY, privateKey),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
        one(field.CRYPTO_TYPE, 2),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun legacy(address: String, publicKey: ByteArray) = slot(
        role.LEGACY_SUBSTRATE,
        "",
        bytes(field.PUBLIC_KEY, publicKey),
        bytes(field.PRIVATE_KEY, ByteArray(32) { 5 }),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, address.toByteArray()),
        one(field.CRYPTO_TYPE, 2),
        one(field.SOURCE_RECIPE, 5),
    )

    private fun chain(accountId: ByteArray) = slot(
        role.CHAIN_ACCOUNT,
        "chain-x",
        bytes(field.PUBLIC_KEY, ByteArray(32) { 6 }),
        bytes(field.PRIVATE_KEY, ByteArray(32) { 7 }),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, accountId),
        one(field.CRYPTO_TYPE, 2),
        bytes(field.CHAIN_NAME, "Chain X".toByteArray()),
        one(field.INITIALIZED_OR_FAVORITE, 1),
        one(field.SOURCE_RECIPE, 0),
    )

    private fun auxiliary(
        key: String,
        platform: Int,
        sourceRole: Int,
        format: Int,
        raw: ByteArray,
    ) = slot(
        role.AUXILIARY_SOURCE,
        key,
        one(field.SOURCE_RECIPE, 0),
        one(field.SOURCE_PLATFORM, platform),
        one(field.SOURCE_SLOT_ROLE, sourceRole),
        one(field.BINDING_KIND, 2),
        one(field.SOURCE_FORMAT, format),
        bytes(field.SOURCE_BYTES, raw),
    )

    private fun auxiliaryWithBinding(
        key: String,
        binding: Int,
        sourceRole: Int,
        raw: ByteArray
    ) = slot(
        role.AUXILIARY_SOURCE, key,
        one(field.SOURCE_RECIPE, 0), one(field.SOURCE_PLATFORM, 1),
        one(field.SOURCE_SLOT_ROLE, sourceRole), one(field.BINDING_KIND, binding),
        one(field.SOURCE_FORMAT, 4), bytes(field.SOURCE_BYTES, raw),
    )

    private fun sourceAddress(): String {
        val first = SubstrateKeypairFactory.generate(
            EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList(),
        )
        return first.publicKey.toAddress(42)
    }

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ) = PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private fun PortableWalletSemanticMaterial.Slot.value(id: Int): ByteArray {
        return fields.single { it.id == id }.value
    }
}
