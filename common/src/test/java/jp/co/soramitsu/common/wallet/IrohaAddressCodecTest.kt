package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.common.utils.IrohaAddressCodec.ErrorCode
import jp.co.soramitsu.common.utils.IrohaAddressCodec.IrohaAddressException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class IrohaAddressCodecTest {

    @Test
    fun `encodes and parses Taira and Nexus golden vectors`() {
        loadVectors().forEach { vector ->
            val iroha = vector.getAsJsonObject("expected").getAsJsonObject("iroha")
            val taira = iroha.getAsJsonObject("taira")
            val nexus = iroha.getAsJsonObject("nexus")

            assertEquals(taira["canonicalHex"].asString, IrohaAddressCodec.canonicalHex(taira["publicKeyHex"].asString))
            assertEquals(nexus["canonicalHex"].asString, IrohaAddressCodec.canonicalHex(nexus["publicKeyHex"].asString))
            assertEquals(
                taira["i105"].asString,
                IrohaAddressCodec.encode(taira["publicKeyHex"].asString, UniversalWalletRegistry.taira.chainDiscriminant)
            )
            assertEquals(
                nexus["i105"].asString,
                IrohaAddressCodec.encode(nexus["publicKeyHex"].asString, UniversalWalletRegistry.nexus.chainDiscriminant)
            )

            assertEquals(
                IrohaAddressCodec.Details(
                    chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant,
                    network = IrohaAddressCodec.NetworkKind.TAIRA,
                    canonicalHex = taira["canonicalHex"].asString,
                    publicKeyHex = taira["publicKeyHex"].asString,
                    i105 = taira["i105"].asString
                ),
                IrohaAddressCodec.parse(taira["i105"].asString, UniversalWalletRegistry.taira.chainDiscriminant)
            )
            assertEquals(
                IrohaAddressCodec.Details(
                    chainDiscriminant = UniversalWalletRegistry.nexus.chainDiscriminant,
                    network = IrohaAddressCodec.NetworkKind.NEXUS,
                    canonicalHex = nexus["canonicalHex"].asString,
                    publicKeyHex = nexus["publicKeyHex"].asString,
                    i105 = nexus["i105"].asString
                ),
                IrohaAddressCodec.parse(nexus["i105"].asString, UniversalWalletRegistry.nexus.chainDiscriminant)
            )
        }
    }

    @Test
    fun `rejects network mismatches and malformed I105 literals`() {
        val iroha = loadVectors().first().getAsJsonObject("expected").getAsJsonObject("iroha")
        val taira = iroha.getAsJsonObject("taira")
        val nexus = iroha.getAsJsonObject("nexus")
        val tairaAddress = taira["i105"].asString
        val nexusAddress = nexus["i105"].asString

        assertError(ErrorCode.ERR_UNEXPECTED_NETWORK_PREFIX) {
            IrohaAddressCodec.parse(tairaAddress, UniversalWalletRegistry.nexus.chainDiscriminant)
        }
        assertError(ErrorCode.ERR_UNEXPECTED_NETWORK_PREFIX) {
            IrohaAddressCodec.parse(nexusAddress, UniversalWalletRegistry.taira.chainDiscriminant)
        }
        assertError(ErrorCode.ERR_CHECKSUM_MISMATCH) {
            IrohaAddressCodec.parse(tamperLastSymbol(tairaAddress), UniversalWalletRegistry.taira.chainDiscriminant)
        }
        assertError(ErrorCode.ERR_INVALID_I105_CHAR) {
            IrohaAddressCodec.parse("${tairaAddress.take(8)}!${tairaAddress.drop(9)}")
        }
        assertError(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT) {
            IrohaAddressCodec.parse(" $tairaAddress")
        }
        assertError(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT) {
            IrohaAddressCodec.parse(taira["canonicalHex"].asString)
        }
        assertError(ErrorCode.ERR_MISSING_I105_SENTINEL) {
            IrohaAddressCodec.parse(nexusAddress.replaceFirst("sora", "\uff53\uff4f\uff52\uff41"))
        }
        assertError(ErrorCode.ERR_INVALID_I105_CHAR) {
            IrohaAddressCodec.parse(nexusAddress.replaceFirst("\uff9b", "\u30ed"))
        }

        assertFalse(IrohaAddressCodec.isValid(tamperLastSymbol(tairaAddress), UniversalWalletRegistry.taira.chainDiscriminant))
        assertEquals(null, IrohaAddressCodec.networkKind(taira["canonicalHex"].asString))
    }

    @Test
    fun `supports canonical custom numeric prefixes only`() {
        val publicKeyHex = loadVectors().first().getAsJsonObject("expected").getAsJsonObject("iroha").getAsJsonObject("taira")["publicKeyHex"].asString
        val custom = IrohaAddressCodec.encode(publicKeyHex, 42)

        assertTrue(custom.startsWith("n42"))
        assertEquals(IrohaAddressCodec.NetworkKind.CUSTOM, IrohaAddressCodec.parse(custom, 42).network)
        assertError(ErrorCode.ERR_UNEXPECTED_NETWORK_PREFIX) {
            IrohaAddressCodec.parse(custom, UniversalWalletRegistry.taira.chainDiscriminant)
        }
        assertError(ErrorCode.ERR_UNSUPPORTED_ADDRESS_FORMAT) {
            IrohaAddressCodec.parse(custom.replaceFirst(Regex("^n42"), "n00042"), 42)
        }
    }

    @Test
    fun `rejects invalid public keys and discriminants before encoding`() {
        val publicKeyHex = loadVectors().first().getAsJsonObject("expected").getAsJsonObject("iroha").getAsJsonObject("taira")["publicKeyHex"].asString

        assertError(ErrorCode.ERR_INVALID_LENGTH) { IrohaAddressCodec.encode("abcd", UniversalWalletRegistry.taira.chainDiscriminant) }
        assertError(ErrorCode.ERR_INVALID_HEX_ADDRESS) {
            IrohaAddressCodec.encode("${publicKeyHex.dropLast(1)}z", UniversalWalletRegistry.taira.chainDiscriminant)
        }
        assertError(ErrorCode.ERR_INVALID_I105_PREFIX) { IrohaAddressCodec.encode(publicKeyHex, -1) }
        assertError(ErrorCode.ERR_INVALID_I105_PREFIX) { IrohaAddressCodec.encode(publicKeyHex, 0x4000) }
    }

    @Test
    fun `rejects checksum-valid payloads outside single-key Ed25519 shape`() {
        val canonicalHex = loadVectors().first().getAsJsonObject("expected").getAsJsonObject("iroha").getAsJsonObject("taira")["canonicalHex"].asString
        val tairaDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant

        assertError(ErrorCode.ERR_INVALID_HEADER_VERSION) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("0x22${canonicalHex.drop(4)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_INVALID_NORM_VERSION) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("0x00${canonicalHex.drop(4)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_UNKNOWN_ADDRESS_CLASS) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("0x12${canonicalHex.drop(4)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_UNEXPECTED_EXTENSION_FLAG) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("0x03${canonicalHex.drop(4)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_UNKNOWN_CONTROLLER_TAG) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("${canonicalHex.take(4)}01${canonicalHex.drop(6)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_UNKNOWN_CURVE) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("${canonicalHex.take(6)}02${canonicalHex.drop(8)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_INVALID_LENGTH) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("${canonicalHex.take(8)}1f${canonicalHex.drop(10)}", tairaDiscriminant))
        }
        assertError(ErrorCode.ERR_UNEXPECTED_TRAILING_BYTES) {
            IrohaAddressCodec.parse(IrohaAddressCodec.encodeCanonicalHex("${canonicalHex}00", tairaDiscriminant))
        }
    }

    private fun loadVectors(): List<JsonObject> {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject.getAsJsonArray("vectors").map { vector -> vector.asJsonObject }
        }
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(IrohaAddressException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private fun tamperLastSymbol(address: String): String {
        val replacement = if (address.endsWith("1")) "2" else "1"
        return address.dropLast(1) + replacement
    }
}
