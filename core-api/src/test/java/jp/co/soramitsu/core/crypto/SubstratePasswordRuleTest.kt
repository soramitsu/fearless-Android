package jp.co.soramitsu.core.crypto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * substrate-password-vectors.json is written by the web repository's
 * scripts/generate-substrate-password-vectors.py (Python standard library only, no client code).
 */
class SubstratePasswordRuleTest {

    @Test
    fun everyRuleSaltsTheMiniSecretAsTheVectorsSay() {
        for (vector in vectors()) {
            val password = SubstrateJunctionDecoder.decode(vector["derivationPath"].asString).password
            for ((rule, key) in RULES) {
                val expected = vector["accounts"].asJsonObject[key].asJsonObject["miniSecret"].asString
                val derived = substrateMiniSecret(vector["mnemonic"].asString, password, rule)
                assertEquals("${vector["id"].asString} $key", expected, "0x" + derived.toHex())
            }
        }
    }

    @Test
    fun theCurrentRuleIsTheSeedThisAppAlwaysDerived() {
        for (vector in vectors()) {
            val mnemonic = vector["mnemonic"].asString
            val password = SubstrateJunctionDecoder.decode(vector["derivationPath"].asString).password
            assertArrayEquals(
                SubstrateSeedFactory.deriveSeed(mnemonic, password).seed.copyOfRange(0, 32),
                substrateMiniSecret(mnemonic, password, SubstratePasswordRule.NFKD)
            )
        }
    }

    @Test
    fun onlyRulesThatReadThePasswordDifferentlyAreOffered() {
        val rules = vectors().associate {
            it["id"].asString to SubstratePasswordRule.distinctFor(SubstrateJunctionDecoder.decode(it["derivationPath"].asString).password)
        }
        assertEquals(listOf(SubstratePasswordRule.NFKD), rules["import12-ascii"])
        assertEquals(listOf(SubstratePasswordRule.NFKD, SubstratePasswordRule.NFC), rules["default24-unicode"])
        assertEquals(
            listOf(SubstratePasswordRule.NFKD, SubstratePasswordRule.NFC, SubstratePasswordRule.AS_TYPED),
            rules["default24-unicode-decomposed-input"]
        )
        assertEquals(listOf(SubstratePasswordRule.NFKD, SubstratePasswordRule.NFC), rules["import12-katakana"])
        assertEquals(listOf(SubstratePasswordRule.NFKD), SubstratePasswordRule.distinctFor(null))
        assertEquals(listOf(SubstratePasswordRule.NFKD), SubstratePasswordRule.distinctFor(""))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun vectors(): List<JsonObject> {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("substrate-password-vectors.json")) {
            "substrate-password-vectors.json is missing from test resources"
        }
        val document = stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        assertEquals("nfkd", document["canonicalRule"].asString)
        return document["vectors"].asJsonArray.map { it.asJsonObject }
    }

    private companion object {
        val RULES = listOf(
            SubstratePasswordRule.NFKD to "nfkd",
            SubstratePasswordRule.NFC to "nfc",
            SubstratePasswordRule.AS_TYPED to "asTyped"
        )
    }
}
