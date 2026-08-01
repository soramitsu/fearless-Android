package jp.co.soramitsu.common.data.secrets

import java.io.ByteArrayOutputStream
import jp.co.soramitsu.common.data.storage.encrypt.MAX_WALLET_SECRET_PLAINTEXT_CHARS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WalletSecretScalePreflightTest {

    @Test
    fun everySupportedSchemaAcceptsACompleteCanonicalPayload() {
        WalletSecretScalePreflight.requireLegacyV04SigningData(
            keyPair(nonce = ByteArray(32)).toCanonicalHex()
        )
        WalletSecretScalePreflight.requireKeyPairV2(
            keyPair().toCanonicalHex()
        )
        WalletSecretScalePreflight.requireSourceV1(sourceV1())
        WalletSecretScalePreflight.requireMetaAccountV2(metaAccountV2())
        WalletSecretScalePreflight.requireChainAccountV2(chainAccountV2())
        WalletSecretScalePreflight.requireSubstrateV3(substrateV3())
        WalletSecretScalePreflight.requireEthereumV3(ethereumV3())
        WalletSecretScalePreflight.requireTonV3(tonV3(seedSize = 64))
        WalletSecretScalePreflight.requireLegacyV69(legacyV69())
    }

    @Test
    fun frozenSixFieldFixtureSelectsLegacyWithoutAnAllocatingTrialDecode() {
        WalletSecretScalePreflight.requireLegacyV69(FROZEN_V69_SECRET)
        assertEquals(
            WalletMetaAccountScaleLayout.LEGACY_V69,
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(
                FROZEN_V69_SECRET
            )
        )
        assertCorruption {
            WalletSecretScalePreflight.requireMetaAccountV2(
                FROZEN_V69_SECRET
            )
        }

        val completeCurrentPayload = FROZEN_V69_SECRET + "00"
        WalletSecretScalePreflight.requireMetaAccountV2(completeCurrentPayload)
        assertEquals(
            WalletMetaAccountScaleLayout.CURRENT_V2,
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(
                completeCurrentPayload
            )
        )
        assertCorruption {
            WalletSecretScalePreflight.requireLegacyV69(
                completeCurrentPayload
            )
        }
    }

    @Test
    fun canonicalHexEnvelopeRejectsAmbiguityBeforeScaleParsing() {
        val valid = tonV3()
        val tooLarge = "0x" + "00".repeat(
            MAX_WALLET_SECRET_PLAINTEXT_CHARS / 2
        )

        listOf(
            "",
            "0x",
            valid.removePrefix("0x"),
            valid.uppercase(),
            "0x0",
            "0x0g",
            tooLarge
        ).forEach { malformed ->
            assertCorruption {
                WalletSecretScalePreflight.requireTonV3(malformed)
            }
        }
    }

    @Test
    fun invalidOptionDiscriminantsAreRejectedAtTopLevelAndInNestedKeypairs() {
        assertCorruption {
            WalletSecretScalePreflight.requireChainAccountV2("0x02")
        }

        val invalidNestedNonce = concatenate(
            absent(),
            absent(),
            keyPair(),
            absent(),
            byteArrayOf(1),
            byteArrayField(ByteArray(32) { 1 }),
            byteArrayField(ByteArray(33) { 2 }),
            byteArrayOf(2)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireLegacyV69(invalidNestedNonce)
        }

        val invalidLegacyV04Nonce = concatenate(
            byteArrayField(ByteArray(32)),
            byteArrayField(ByteArray(32)),
            byteArrayOf(2)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireLegacyV04SigningData(
                invalidLegacyV04Nonce
            )
        }
        assertCorruption {
            WalletSecretScalePreflight.requireKeyPairV2(
                invalidLegacyV04Nonce
            )
        }
    }

    @Test
    fun fieldBoundsAreInclusiveAndRejectEveryOversizedFieldBeforeItsBody() {
        WalletSecretScalePreflight.requireTonV3(
            tonV3(
                seedSize = 8_192,
                privateKeySize = 64,
                publicKeySize = 65
            )
        )

        val excessiveSeedClaim = concatenate(
            byteArrayOf(1),
            compact(8_193)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireChainAccountV2(
                excessiveSeedClaim
            )
        }

        val excessivePrivateKeyClaim = concatenate(
            byteArrayField(ByteArray(0)),
            compact(65)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireTonV3(
                excessivePrivateKeyClaim
            )
        }

        val excessivePublicKeyClaim = concatenate(
            byteArrayField(ByteArray(0)),
            byteArrayField(ByteArray(32)),
            compact(66)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireTonV3(
                excessivePublicKeyClaim
            )
        }

        val excessiveNonceClaim = concatenate(
            absent(),
            absent(),
            byteArrayField(ByteArray(32)),
            byteArrayField(ByteArray(32)),
            byteArrayOf(1),
            compact(65)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireChainAccountV2(
                excessiveNonceClaim
            )
        }
    }

    @Test
    fun excessiveStringsAndTruncatedFieldsAreRejectedWithoutClaimedAllocation() {
        val excessiveTypeClaim = compact(65).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireSourceV1(excessiveTypeClaim)
        }

        val truncatedTonSeed = concatenate(
            compact(32),
            ByteArray(31)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireTonV3(truncatedTonSeed)
        }

        val excessivePathClaim = concatenate(
            scaleString("CREATE"),
            byteArrayField(ByteArray(32)),
            byteArrayField(ByteArray(32)),
            absent(),
            absent(),
            absent(),
            byteArrayOf(1),
            compact(8_193)
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireSourceV1(excessivePathClaim)
        }

        val hostileLegacyPrivateKeyClaim = byteArrayOf(
            0x03,
            0x00,
            0x00,
            0x00,
            0x40
        ).toCanonicalHex()
        assertCorruption {
            WalletSecretScalePreflight.requireLegacyV04SigningData(
                hostileLegacyPrivateKeyClaim
            )
        }
        assertCorruption {
            WalletSecretScalePreflight.requireKeyPairV2(
                hostileLegacyPrivateKeyClaim
            )
        }
    }

    @Test
    fun allCompactModesAreHandledWithCanonicalAndIntegerSafeRules() {
        WalletSecretScalePreflight.requireTonV3(tonV3(seedSize = 63))
        WalletSecretScalePreflight.requireTonV3(tonV3(seedSize = 64))

        val noncanonicalTwoByte = byteArrayOf(0xfd.toByte(), 0x00)
        val truncatedTwoByte = byteArrayOf(0x01)
        val noncanonicalFourByte = byteArrayOf(
            0xfe.toByte(),
            0xff.toByte(),
            0x00,
            0x00
        )
        val truncatedFourByte = byteArrayOf(
            0x02,
            0x00,
            0x01
        )
        val canonicalFourByteButExcessive = byteArrayOf(
            0x02,
            0x00,
            0x01,
            0x00
        )
        val noncanonicalBigInteger = byteArrayOf(
            0x03,
            0xff.toByte(),
            0xff.toByte(),
            0xff.toByte(),
            0x3f
        )
        val canonicalThirtyBitButExcessive = byteArrayOf(
            0x03,
            0x00,
            0x00,
            0x00,
            0x40
        )
        val largerThanInt = byteArrayOf(
            0x03,
            0x00,
            0x00,
            0x00,
            0x80.toByte()
        )
        val largerThanFourBytes = byteArrayOf(
            0x07,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01
        )
        val redundantBigIntegerWidth = byteArrayOf(
            0x07,
            0x00,
            0x00,
            0x00,
            0x40,
            0x00
        )
        val truncatedMaximumWidth = byteArrayOf(
            0xff.toByte(),
            0x01
        )

        listOf(
            noncanonicalTwoByte,
            truncatedTwoByte,
            noncanonicalFourByte,
            truncatedFourByte,
            canonicalFourByteButExcessive,
            noncanonicalBigInteger,
            canonicalThirtyBitButExcessive,
            largerThanInt,
            largerThanFourBytes,
            redundantBigIntegerWidth,
            truncatedMaximumWidth
        ).forEach { hostileLength ->
            assertCorruption {
                WalletSecretScalePreflight.requireTonV3(
                    hostileLength.toCanonicalHex()
                )
            }
        }
    }

    @Test
    fun stringsRequireCanonicalUtf8WithoutMaterializingTheString() {
        val overlongNull = concatenate(
            compact(2),
            byteArrayOf(0xc0.toByte(), 0x80.toByte())
        ).toCanonicalHex()
        val surrogate = concatenate(
            compact(3),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte())
        ).toCanonicalHex()
        val truncatedSequence = concatenate(
            compact(2),
            byteArrayOf(0xe2.toByte(), 0x82.toByte())
        ).toCanonicalHex()

        listOf(overlongNull, surrogate, truncatedSequence).forEach { invalid ->
            assertCorruption {
                WalletSecretScalePreflight.requireSourceV1(invalid)
            }
        }

        val validUnicodeSource = sourceV1(type = "\uD83D\uDD10")
        WalletSecretScalePreflight.requireSourceV1(validUnicodeSource)
    }

    @Test
    fun exactSchemaCompletionRejectsTrailingBytesAndPartialCurrentSuffixes() {
        assertCorruption {
            WalletSecretScalePreflight.requireTonV3(tonV3() + "00")
        }
        assertCorruption {
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(
                legacyV69() + "01"
            )
        }
        assertCorruption {
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(
                metaAccountV2() + "00"
            )
        }

        val currentWithTon = metaAccountV2(tonKeyPair = keyPair())
        assertEquals(
            WalletMetaAccountScaleLayout.CURRENT_V2,
            WalletSecretScalePreflight.requireMetaAccountV2OrLegacyV69(
                currentWithTon
            )
        )
    }

    private fun sourceV1(type: String = "CREATE"): String {
        return concatenate(
            scaleString(type),
            byteArrayField(ByteArray(32) { 1 }),
            byteArrayField(ByteArray(32) { 2 }),
            some(byteArrayField(ByteArray(32) { 3 })),
            some(byteArrayField(ByteArray(64) { 4 })),
            some(scaleString("alpha beta gamma delta")),
            some(scaleString("//hard///password"))
        ).toCanonicalHex()
    }

    private fun legacyV69(): String {
        return concatenate(
            some(byteArrayField(ByteArray(32) { 5 })),
            some(byteArrayField(ByteArray(64) { 6 })),
            keyPair(nonce = ByteArray(32) { 7 }),
            some(scaleString("//substrate")),
            some(keyPair(publicKeySize = 33)),
            some(scaleString("m/44'/60'/0'/0/0"))
        ).toCanonicalHex()
    }

    private fun metaAccountV2(tonKeyPair: ByteArray? = null): String {
        return concatenate(
            legacyV69().canonicalHexToBytes(),
            if (tonKeyPair == null) absent() else some(tonKeyPair)
        ).toCanonicalHex()
    }

    private fun chainAccountV2(): String {
        return concatenate(
            some(byteArrayField(ByteArray(32) { 8 })),
            absent(),
            keyPair(),
            some(scaleString("//chain"))
        ).toCanonicalHex()
    }

    private fun substrateV3(): String = chainAccountV2()

    private fun ethereumV3(): String {
        return concatenate(
            some(byteArrayField(ByteArray(32) { 9 })),
            some(byteArrayField(ByteArray(64) { 10 })),
            keyPair(publicKeySize = 33),
            some(scaleString("m/44'/60'/0'/0/0"))
        ).toCanonicalHex()
    }

    private fun tonV3(
        seedSize: Int = 32,
        privateKeySize: Int = 32,
        publicKeySize: Int = 32
    ): String {
        return concatenate(
            byteArrayField(ByteArray(seedSize) { 11 }),
            byteArrayField(ByteArray(privateKeySize) { 12 }),
            byteArrayField(ByteArray(publicKeySize) { 13 })
        ).toCanonicalHex()
    }

    private fun keyPair(
        privateKeySize: Int = 32,
        publicKeySize: Int = 32,
        nonce: ByteArray? = null
    ): ByteArray {
        return concatenate(
            byteArrayField(ByteArray(privateKeySize) { 14 }),
            byteArrayField(ByteArray(publicKeySize) { 15 }),
            nonce?.let { some(byteArrayField(it)) } ?: absent()
        )
    }

    private fun byteArrayField(value: ByteArray): ByteArray {
        return concatenate(compact(value.size), value)
    }

    private fun scaleString(value: String): ByteArray {
        return byteArrayField(value.toByteArray(Charsets.UTF_8))
    }

    private fun some(encodedValue: ByteArray): ByteArray {
        return concatenate(byteArrayOf(1), encodedValue)
    }

    private fun absent(): ByteArray = byteArrayOf(0)

    private fun compact(value: Int): ByteArray {
        require(value >= 0)
        return when {
            value < 1 shl 6 -> byteArrayOf((value shl 2).toByte())
            value < 1 shl 14 -> {
                val encoded = (value shl 2) or 0b01
                littleEndian(encoded.toLong(), 2)
            }

            value < 1 shl 30 -> {
                val encoded = (value.toLong() shl 2) or 0b10
                littleEndian(encoded, 4)
            }

            else -> error("Test fixture does not need a big-integer compact")
        }
    }

    private fun littleEndian(value: Long, bytes: Int): ByteArray {
        return ByteArray(bytes) { index ->
            (value ushr (index * 8)).toByte()
        }
    }

    private fun concatenate(vararg pieces: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(
            pieces.sumOf { it.size }
        )
        pieces.forEach { piece ->
            output.write(piece, 0, piece.size)
        }
        return output.toByteArray()
    }

    private fun ByteArray.toCanonicalHex(): String {
        return buildString(2 + size * 2) {
            append("0x")
            for (byte in this@toCanonicalHex) {
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    private fun String.canonicalHexToBytes(): ByteArray {
        require(startsWith("0x") && length % 2 == 0)
        return ByteArray((length - 2) / 2) { byteIndex ->
            val characterIndex = 2 + byteIndex * 2
            (
                HEX_DIGITS.indexOf(this[characterIndex]) * 16 +
                    HEX_DIGITS.indexOf(this[characterIndex + 1])
                ).toByte()
        }
    }

    private fun assertCorruption(block: () -> Unit) {
        assertThrows(
            WalletSecretScaleCorruptionException::class.java
        ) {
            block()
        }
    }

    private companion object {
        const val HEX_DIGITS = "0123456789abcdef"

        const val FROZEN_V69_SECRET =
            "0x0140030a11181f262d343b424950575e656c01809bbfb738f88e7608a4a843080793117250e4c26b9e7effd2b2" +
                "29eebb598a70da80a219f7f735535d75f8bb32279d8b8c6c29c79c11bfcd16fedcd2ea4beefb8908800479460640" +
                "3e94003e40576b1309e8d3af7e20b1698dee7bc2eef5e73d7e7f1a01808b5e4d8f16a927de85fdb23b10fc8657db" +
                "269832ee57efe6d8f91ae9d827e31c01442f2f686172642f2f2f70617373776f72640180ea36187041e275eb6502" +
                "9830df60700f4f6793bc044a990eb4d0af8a2aa7dd468403f21bbcf796503ce43257aa19bccd970694acfed2e38e" +
                "3485b9efcf57b3d684c700013c2f2f34342f2f36302f2f302f302f30"
    }
}
