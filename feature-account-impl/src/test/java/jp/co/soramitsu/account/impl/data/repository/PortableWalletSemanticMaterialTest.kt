package jp.co.soramitsu.account.impl.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

class PortableWalletSemanticMaterialTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId
    private val metadata = PortableWalletSemanticMaterial.MetadataId

    @Test
    fun `single EVM wallet has a complete stable golden vector`() {
        val source = evmSnapshot()
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(EVM_VECTOR, encoded.hex())
            assertArrayEquals(encoded, codec.encode(decoded))
            assertEquals(0, decoded.selectedIndex)
            assertEquals(7L, decoded.wallets.single().sourcePosition)
            assertEquals("PortableWalletSemanticMaterial.Snapshot(redacted)", decoded.toString())
            assertEquals("PortableWalletSemanticMaterial.Wallet(redacted)", decoded.wallets.single().toString())
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `multi-root wallet preserves TON chain V1 metadata and auxiliary source bytes`() {
        val source = multiRootSnapshot()
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(MULTI_ROOT_SHA256, encoded.sha256())
            assertArrayEquals(encoded, codec.encode(decoded))
            assertEquals((1..7).toList(), decoded.wallets.single().slots.map { it.role }.distinct())
            assertEquals(listOf(1, 5, 8), decoded.wallets.single().metadata.map { it.id })
            assertArrayEquals(
                byteArrayOf(0x55),
                decoded.wallets.single().slots[6].fields
                .single { it.id == field.SOURCE_BYTES }.value
            )
            assertArrayEquals(
                byteArrayOf(0x66),
                decoded.wallets.single().slots[7].fields
                .single { it.id == field.SOURCE_BYTES }.value
            )
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `ordered wallets preserve selection and distinct portable IDs`() {
        val first = evmWallet()
        val second = wallet(
            idStart = 32,
            position = 3,
            slots = listOf(
                slot(
                    role.WATCH_IDENTITY, "0000",
                bytes(field.ACCOUNT_ID_OR_ADDRESS, 9),
                bytes(field.WATCH_ECOSYSTEM, 2)
                )
            )
        )
        val source = PortableWalletSemanticMaterial.Snapshot(1, listOf(first, second))
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(1, decoded.selectedIndex)
            assertEquals(7L, decoded.wallets[0].sourcePosition)
            assertEquals(3L, decoded.wallets[1].sourcePosition)
            assertArrayEquals(first.portableId, decoded.wallets[0].portableId)
            assertArrayEquals(second.portableId, decoded.wallets[1].portableId)
            assertArrayEquals(encoded, codec.encode(decoded))
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `all nine wallet metadata values survive canonical roundtrip`() {
        val fields = listOf(
            PortableWalletSemanticMaterial.Metadata(metadata.ASSET_KEYS_ORDER, stringList("DOT", "ETH")),
            PortableWalletSemanticMaterial.Metadata(metadata.UNUSED_CHAIN_IDS, stringList("sora")),
            PortableWalletSemanticMaterial.Metadata(
                metadata.SELECTED_CURRENCY,
                "USD".toByteArray(Charsets.UTF_8)
            ),
            PortableWalletSemanticMaterial.Metadata(
                metadata.NETWORK_MANAGEMENT_FILTER,
                "all".toByteArray(Charsets.UTF_8)
            ),
            PortableWalletSemanticMaterial.Metadata(metadata.ASSET_VISIBILITY, visibility("DOT", true)),
            PortableWalletSemanticMaterial.Metadata(metadata.FAVORITE_CHAIN_IDS, stringList("sora")),
            PortableWalletSemanticMaterial.Metadata(
                metadata.ASSET_FILTER_OPTIONS,
                stringList("hidden", "visible")
            ),
            PortableWalletSemanticMaterial.Metadata(metadata.ZERO_BALANCE_ASSETS_HIDDEN, byteArrayOf(1)),
            PortableWalletSemanticMaterial.Metadata(metadata.CAN_EXPORT_ETHEREUM_MNEMONIC, byteArrayOf(0))
        )
        val source = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(wallet(metadata = fields, slots = listOf(evmSlot())))
        )
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(FULL_METADATA_SHA256, encoded.sha256())
            assertEquals((1..9).toList(), decoded.wallets.single().metadata.map { it.id })
            fields.zip(decoded.wallets.single().metadata).forEach { (before, after) ->
                assertArrayEquals(before.value, after.value)
            }
            assertArrayEquals(encoded, codec.encode(decoded))
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `Android display metadata preserves absent empty and exact UTF-8 values`() {
        val selected = codec.encodeMetadataText("")
        val filter = codec.encodeMetadataText("All / 日本語")
        val source = PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                wallet(
                    metadata = listOf(
                        PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_SELECTED_CHAIN_ID, selected),
                        PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_CHAIN_SELECT_FILTER, filter)
                    ),
                    slots = listOf(evmSlot())
                )
            )
        )
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(listOf(10, 11), decoded.wallets.single().metadata.map { it.id })
            assertArrayEquals(byteArrayOf(), decoded.wallets.single().metadata[0].value)
            assertArrayEquals("All / 日本語".toByteArray(Charsets.UTF_8), decoded.wallets.single().metadata[1].value)
            assertArrayEquals(encoded, codec.encode(decoded))
            assertEquals(emptyList<Int>(), evmWallet().metadata.map { it.id })
            val unknown = encoded.copyOf().also { it[38] = 13 }
            try {
                assertThrows(IllegalArgumentException::class.java) { codec.decode(unknown).clearSecrets() }
            } finally {
                unknown.fill(0)
            }
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `Android display metadata matches cross-platform watch wallet vector`() {
        val watch = PortableWalletSemanticMaterial.Wallet(
            portableId = ByteArray(16) { 0x33 },
            sourcePosition = 0,
            initialized = true,
            name = "watch",
            metadata = listOf(
                PortableWalletSemanticMaterial.Metadata(
                    metadata.ANDROID_SELECTED_CHAIN_ID, "sora".toByteArray(Charsets.UTF_8)
                ),
                PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_CHAIN_SELECT_FILTER, byteArrayOf())
            ),
            slots = listOf(
                slot(role.WATCH_IDENTITY, "0000", bytes(field.ACCOUNT_ID_OR_ADDRESS, 9), bytes(field.WATCH_ECOSYSTEM, 2))
            )
        )
        val source = PortableWalletSemanticMaterial.Snapshot(0, listOf(watch))
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(DISPLAY_WATCH_VECTOR, encoded.hex())
            assertEquals(DISPLAY_WATCH_SHA256, encoded.sha256())
            assertArrayEquals(encoded, codec.encode(decoded))
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `Android asset presentation matches cross-platform watch wallet vector`() {
        val rows = listOf(
            PortableWalletAssetRowPresentation.Row("sora", "dot", emptyList(), 1, -2, true, ""),
            PortableWalletAssetRowPresentation.Row(
                "sora", "dot", listOf(1, 0x80.toByte()), 0, Int.MAX_VALUE, false, "Main"
            )
        )
        val watch = PortableWalletSemanticMaterial.Wallet(
            portableId = ByteArray(16) { 0x33 },
            sourcePosition = 0,
            initialized = true,
            name = "watch",
            metadata = listOf(
                PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_SELECTED_CHAIN_ID, "sora".toByteArray()),
                PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_CHAIN_SELECT_FILTER, byteArrayOf()),
                PortableWalletSemanticMaterial.Metadata(
                    metadata.ANDROID_ASSET_ROW_PRESENTATION,
                    PortableWalletAssetRowPresentation.encode(rows)
                )
            ),
            slots = listOf(
                slot(role.WATCH_IDENTITY, "0000", bytes(field.ACCOUNT_ID_OR_ADDRESS, 9), bytes(field.WATCH_ECOSYSTEM, 2))
            )
        )
        val source = PortableWalletSemanticMaterial.Snapshot(0, listOf(watch))
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertEquals(ASSET_WATCH_VECTOR, encoded.hex())
            assertEquals(ASSET_WATCH_SHA256, encoded.sha256())
            assertEquals(rows, PortableWalletAssetRowPresentation.decode(decoded.wallets.single().metadata[2].value))
            assertArrayEquals(encoded, codec.encode(decoded))
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `Android display metadata rejects malformed text and noncanonical tags`() {
        assertThrows(IllegalArgumentException::class.java) { codec.encodeMetadataText("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { codec.encodeMetadataText("x".repeat(2_049)) }
        val malformed = listOf(
            listOf(PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_SELECTED_CHAIN_ID, byteArrayOf(0xff.toByte()))),
            listOf(PortableWalletSemanticMaterial.Metadata(13, byteArrayOf())),
            listOf(
                PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_CHAIN_SELECT_FILTER, byteArrayOf()),
                PortableWalletSemanticMaterial.Metadata(metadata.ANDROID_SELECTED_CHAIN_ID, byteArrayOf())
            )
        ).map { PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(metadata = it, slots = listOf(evmSlot())))) }
        try {
            malformed.forEach { assertThrows(IllegalArgumentException::class.java) { codec.encode(it) } }
        } finally {
            malformed.forEach { it.clearSecrets() }
        }
    }

    @Test
    fun `address-only TON watch identity has no signing material`() {
        val watch = slot(
            role.WATCH_IDENTITY, "0000",
            bytes(field.ACCOUNT_ID_OR_ADDRESS, 1, 2, 3),
            bytes(field.TON_CONTRACT_VERSION, 2),
            bytes(field.TON_ADDRESS_ENCODING, 2),
            bytes(field.WATCH_ECOSYSTEM, 3)
        )
        val stray = slot(
            role.AUXILIARY_SOURCE, "0000",
            bytes(field.SOURCE_RECIPE, 0),
            bytes(field.SOURCE_PLATFORM, 2),
            bytes(field.SOURCE_SLOT_ROLE, 3),
            bytes(field.BINDING_KIND, 1),
            bytes(field.SOURCE_FORMAT, 1),
            bytes(field.SOURCE_BYTES, 0x42)
        )
        val source = PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(stray, watch))))
        val encoded = codec.encode(source)
        val decoded = codec.decode(encoded)
        try {
            assertArrayEquals(encoded, codec.encode(decoded))
            assertEquals(
                listOf(role.AUXILIARY_SOURCE, role.WATCH_IDENTITY),
                decoded.wallets.single().slots.map { it.role }
            )
        } finally {
            source.clearSecrets()
            decoded.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `decoder rejects truncated extended unknown and noncanonical records`() {
        val source = evmSnapshot()
        val encoded = codec.encode(source)
        try {
            val invalid = listOf(
                encoded.copyOf(encoded.size - 1),
                encoded + byteArrayOf(0),
                encoded.copyOf().also { it[8] = 2 },
                encoded.copyOf().also { it[33] = 2 },
                encoded.copyOf().also { it[40] = 99 },
                encoded.copyOf().also { it[44] = 99 },
                encoded.copyOf().also { it[43] = 5 },
                ByteArray(MAX_ENCODED_BYTES + 1)
            )
            invalid.forEach { candidate ->
                assertThrows(Exception::class.java) { codec.decode(candidate).clearSecrets() }
                candidate.fill(0)
            }
            val invalidUtf8 = encoded.copyOf().also { it[36] = 0xff.toByte() }
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    codec.decode(invalidUtf8).clearSecrets()
                }
            } finally {
                invalidUtf8.fill(0)
            }
        } finally {
            source.clearSecrets()
            encoded.fill(0)
        }
    }

    @Test
    fun `encoder rejects duplicate identities missing fields wrong bindings and conflicting favorites`() {
        val good = evmWallet()
        val malformed = listOf(
            PortableWalletSemanticMaterial.Snapshot(0, listOf(good, good)),
            PortableWalletSemanticMaterial.Snapshot(
                0,
                listOf(
                    wallet(
                        slots = listOf(
                            slot(
                                role.EVM_ROOT, "",
                bytes(field.PUBLIC_KEY, 1), bytes(field.ACCOUNT_ID_OR_ADDRESS, 2),
                bytes(field.SOURCE_RECIPE, 0)
                            )
                        )
                    )
                )
            ),
            PortableWalletSemanticMaterial.Snapshot(
                0,
                listOf(
                    wallet(
                        slots = listOf(
                            slot(
                                role.EVM_ROOT, "",
                bytes(field.PUBLIC_KEY, 1), bytes(field.PRIVATE_KEY, 2),
                bytes(field.ACCOUNT_ID_OR_ADDRESS, 3), bytes(99, 4),
                bytes(field.SOURCE_RECIPE, 0)
                            )
                        )
                    )
                )
            ),
            PortableWalletSemanticMaterial.Snapshot(
                0,
                listOf(
                    wallet(
                        slots = listOf(
                            evmSlot(),
                slot(
                    role.AUXILIARY_SOURCE, "0000",
                    bytes(field.SOURCE_RECIPE, 0), bytes(field.SOURCE_PLATFORM, 1),
                    bytes(field.SOURCE_SLOT_ROLE, 14), bytes(field.BINDING_KIND, 5),
                    text(field.BINDING_CHAIN_ID, "missing"), bytes(field.SOURCE_FORMAT, 3),
                    bytes(field.SOURCE_BYTES, 1), bytes(field.BINDING_ACCOUNT_ID, 2)
                )
                        )
                    )
                )
            ),
            PortableWalletSemanticMaterial.Snapshot(
                0,
                listOf(
                    wallet(
                metadata = listOf(PortableWalletSemanticMaterial.Metadata(metadata.FAVORITE_CHAIN_IDS, stringList("chain"))),
                slots = listOf(
                    evmSlot(),
                    slot(
                        role.FAVORITE_CHAIN, "chain",
                    bytes(field.INITIALIZED_OR_FAVORITE, 1)
                    )
                )
                    )
                )
            )
        )
        try {
            malformed.forEach { assertThrows(Exception::class.java) { codec.encode(it) } }
        } finally {
            malformed.forEach { it.clearSecrets() }
        }
    }

    @Test
    fun `encoder rejects mismatched TON address and source role combinations`() {
        val missingTonAddress = slot(
            role.TON_ROOT, "",
            bytes(field.PUBLIC_KEY, 1), bytes(field.PRIVATE_KEY, 2),
            bytes(field.SOURCE_RECIPE, 0), bytes(field.TON_CONTRACT_VERSION, 2),
            bytes(field.TON_ADDRESS_ENCODING, 1)
        )
        val wrongSource = slot(
            role.AUXILIARY_SOURCE, "0000",
            bytes(field.SOURCE_RECIPE, 0), bytes(field.SOURCE_PLATFORM, 2),
            bytes(field.SOURCE_SLOT_ROLE, 10), bytes(field.BINDING_KIND, 1),
            bytes(field.SOURCE_FORMAT, 1), bytes(field.SOURCE_BYTES, 1)
        )
        val wrongCanonicalAddress = slot(
            role.TON_ROOT, "",
            bytes(field.PUBLIC_KEY, 1), bytes(field.PRIVATE_KEY, 2),
            bytes(field.ACCOUNT_ID_OR_ADDRESS, 1, 2, 3), bytes(field.SOURCE_RECIPE, 0),
            bytes(field.TON_CONTRACT_VERSION, 2), bytes(field.TON_ADDRESS_ENCODING, 1)
        )
        val nonUtf8TonSwiftAddress = slot(
            role.TON_ROOT, "",
            bytes(field.PUBLIC_KEY, 1), bytes(field.PRIVATE_KEY, 2),
            bytes(field.ACCOUNT_ID_OR_ADDRESS, 0xff), bytes(field.SOURCE_RECIPE, 0),
            bytes(field.TON_CONTRACT_VERSION, 2), bytes(field.TON_ADDRESS_ENCODING, 2)
        )
        val wrongTonContract = slot(
            role.TON_ROOT, "",
            bytes(field.PUBLIC_KEY, 1), bytes(field.PRIVATE_KEY, 2),
            PortableWalletSemanticMaterial.Field(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(33)),
            bytes(field.SOURCE_RECIPE, 0), bytes(field.TON_CONTRACT_VERSION, 1),
            bytes(field.TON_ADDRESS_ENCODING, 1)
        )
        val wrongWatchContract = slot(
            role.WATCH_IDENTITY, "0000",
            PortableWalletSemanticMaterial.Field(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(33)),
            bytes(field.TON_CONTRACT_VERSION, 0), bytes(field.TON_ADDRESS_ENCODING, 1),
            bytes(field.WATCH_ECOSYSTEM, 3)
        )
        val malformed = listOf(
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(missingTonAddress)))),
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(wrongCanonicalAddress)))),
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(nonUtf8TonSwiftAddress)))),
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(wrongTonContract)))),
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(wrongWatchContract)))),
            PortableWalletSemanticMaterial.Snapshot(0, listOf(wallet(slots = listOf(evmSlot(), wrongSource))))
        )
        try {
            malformed.forEach { assertThrows(Exception::class.java) { codec.encode(it) } }
        } finally {
            malformed.forEach { it.clearSecrets() }
        }
    }

    private fun evmSnapshot() = PortableWalletSemanticMaterial.Snapshot(0, listOf(evmWallet()))

    private fun evmWallet() = wallet(slots = listOf(evmSlot()))

    private fun evmSlot() = slot(
        role.EVM_ROOT, "",
        bytes(field.PUBLIC_KEY, 2, 3), bytes(field.PRIVATE_KEY, 4),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, 5), bytes(field.SOURCE_RECIPE, 0)
    )

    private fun multiRootSnapshot(): PortableWalletSemanticMaterial.Snapshot {
        val chainId = "chain-a"
        val slots = listOf(
            slot(
                role.SUBSTRATE_ROOT, "", bytes(field.PUBLIC_KEY, 1),
                bytes(field.PRIVATE_KEY, 2), bytes(field.ACCOUNT_ID_OR_ADDRESS, 3),
                bytes(field.CRYPTO_TYPE, 1), bytes(field.SOURCE_RECIPE, 0)
            ),
            slot(
                role.EVM_ROOT, "", bytes(field.PUBLIC_KEY, 4), bytes(field.PRIVATE_KEY, 5),
                bytes(field.ACCOUNT_ID_OR_ADDRESS, 6), bytes(field.SOURCE_RECIPE, 0)
            ),
            slot(
                role.TON_ROOT, "", bytes(field.PUBLIC_KEY, 7), bytes(field.PRIVATE_KEY, 8),
                bytes(field.SEED, 9),
                PortableWalletSemanticMaterial.Field(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(33) { it.toByte() }),
                bytes(field.SOURCE_RECIPE, 0), bytes(field.TON_CONTRACT_VERSION, 2),
                bytes(field.TON_ADDRESS_ENCODING, 1)
            ),
            slot(
                role.LEGACY_SUBSTRATE, "", bytes(field.PUBLIC_KEY, 1),
                bytes(field.PRIVATE_KEY, 10), bytes(field.SEED, 11),
                text(field.ACCOUNT_ID_OR_ADDRESS, "5abc"), bytes(field.CRYPTO_TYPE, 1),
                bytes(field.SOURCE_RECIPE, 2)
            ),
            slot(
                role.CHAIN_ACCOUNT, chainId, bytes(field.PUBLIC_KEY, 12),
                bytes(field.PRIVATE_KEY, 13), bytes(field.ACCOUNT_ID_OR_ADDRESS, 14),
                bytes(field.CRYPTO_TYPE, 1), text(field.CHAIN_NAME, "Chain"),
                bytes(field.INITIALIZED_OR_FAVORITE, 1), bytes(field.SOURCE_RECIPE, 0)
            ),
            slot(role.FAVORITE_CHAIN, "chain-z", bytes(field.INITIALIZED_OR_FAVORITE, 1)),
            slot(
                role.AUXILIARY_SOURCE, "0000", bytes(field.SOURCE_RECIPE, 0),
                bytes(field.SOURCE_PLATFORM, 2), bytes(field.SOURCE_SLOT_ROLE, 9),
                bytes(field.BINDING_KIND, 1), bytes(field.SOURCE_FORMAT, 1),
                bytes(field.SOURCE_BYTES, 0x55)
            ),
            slot(
                role.AUXILIARY_SOURCE, "0001", bytes(field.SOURCE_RECIPE, 0),
                bytes(field.SOURCE_PLATFORM, 1), bytes(field.SOURCE_SLOT_ROLE, 14),
                bytes(field.BINDING_KIND, 5), text(field.BINDING_CHAIN_ID, chainId),
                bytes(field.SOURCE_FORMAT, 3), bytes(field.SOURCE_BYTES, 0x66),
                bytes(field.BINDING_ACCOUNT_ID, 14)
            )
        )
        val metadata = listOf(
            PortableWalletSemanticMaterial.Metadata(metadata.ASSET_KEYS_ORDER, stringList("DOT", "ETH")),
            PortableWalletSemanticMaterial.Metadata(metadata.ASSET_VISIBILITY, visibility("DOT", true)),
            PortableWalletSemanticMaterial.Metadata(metadata.ZERO_BALANCE_ASSETS_HIDDEN, byteArrayOf(1))
        )
        return PortableWalletSemanticMaterial.Snapshot(
            0,
            listOf(
                wallet(
                    idStart = 0xa0, position = 9,
            name = "All", metadata = metadata, slots = slots
                )
            )
        )
    }

    private fun wallet(
        idStart: Int = 1,
        position: Long = 7,
        name: String = "E",
        metadata: List<PortableWalletSemanticMaterial.Metadata> = emptyList(),
        slots: List<PortableWalletSemanticMaterial.Slot>
    ) = PortableWalletSemanticMaterial.Wallet(
        ByteArray(16) { (idStart + it).toByte() }, position, true,
        name, metadata, slots
    )

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field
    ) = PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })

    private fun bytes(id: Int, vararg values: Int) =
        PortableWalletSemanticMaterial.Field(id, values.map(Int::toByte).toByteArray())

    private fun text(id: Int, value: String) =
        PortableWalletSemanticMaterial.Field(id, value.toByteArray(Charsets.UTF_8))

    private fun stringList(vararg values: String): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { writer ->
            writer.writeShort(values.size)
            values.forEach { value ->
                val bytes = value.toByteArray(Charsets.UTF_8)
                writer.writeShort(bytes.size)
                writer.write(bytes)
            }
        }
    }.toByteArray()

    private fun visibility(key: String, visible: Boolean): ByteArray = ByteArrayOutputStream().also { output ->
        DataOutputStream(output).use { writer ->
            val bytes = key.toByteArray(Charsets.UTF_8)
            writer.writeShort(1)
            writer.writeShort(bytes.size)
            writer.write(bytes)
            writer.writeByte(if (visible) 1 else 0)
        }
    }.toByteArray()

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).hex()

    private companion object {
        const val MAX_ENCODED_BYTES = 256 * 1024 - 44 - 16
        const val EVM_VECTOR = "4650574d534d303101000100000102030405060708090a0b0c0d0e0f10000000070100014500000102000004010002020302000104070001050b000100"
        const val MULTI_ROOT_SHA256 = "784647ca5aa76953d4d19404d7fe78c461b9df329a2dd698cc8b5cc18b49d22c"
        const val FULL_METADATA_SHA256 = "181f843dcbafbd0ba151a7476b1f63c3a6060df9c9fd45055869ff8383608b16"
        const val DISPLAY_WATCH_VECTOR = "4650574d534d3031010001000033333333333333333333333333333333000000000100057761746368020a0004736f72610b0000000108000430303030020700010916000102"
        const val DISPLAY_WATCH_SHA256 = "e8959e6aa11fd339fd92ddd65798beda0b6443fe1d1be60f6ad219e2d26c3477"
        const val ASSET_WATCH_VECTOR =
            "4650574d534d303101000100003333333333333333333333333333333300000000010005776174636803" +
                "0a0004736f72610b00000c00350100020004736f72610003646f74000002fffffffe010100000004" +
                "736f72610003646f7400020180017fffffff000100044d61696e0001080004303030300207000109" +
                "16000102"
        const val ASSET_WATCH_SHA256 = "842124d8aa738dc490b5f1366470f9e3183158514236b3c6ba4758bb927a66ab"
    }
}
