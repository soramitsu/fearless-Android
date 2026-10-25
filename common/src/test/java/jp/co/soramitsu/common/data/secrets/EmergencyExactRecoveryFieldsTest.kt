package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyExactRecoveryFieldsTest {
    @Test
    fun substrateExportsEveryStoredFieldExactly() = verifyStore("SubstrateSecrets(V3)", 176)

    @Test
    fun ethereumExportsEveryStoredFieldExactly() = verifyStore("EthereumSecrets(V3)", 26)

    @Test
    fun tonExportsEveryStoredFieldExactly() = verifyStore("TonSecrets(V3)", 24)

    @Test
    fun networkAccountsExportEveryStoredFieldExactly() = verifyStore("ChainAccountSecrets(V2)", 33)

    private fun verifyStore(store: String, expectedCount: Int) {
        val stream = checkNotNull(javaClass.getResourceAsStream("/emergency-exact-fields-v1.json"))
        val root = InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
        assertEquals(259, root["count"].asInt)
        val vectors = root.getAsJsonArray("vectors").map { it.asJsonObject }
        assertEquals(259, vectors.size)
        val selected = vectors.filter { it["store"].asString == store }
        assertEquals(expectedCount, selected.size)
        selected.forEach { vector ->
            val context = vector["case"].asString
            val key = vector["storageKey"].asString
            val stored = vector["stored"].asString
            val preferences = HashMapEncryptedPreferences()
            preferences.putEncryptedString(key, stored)
            val expected = vector.getAsJsonArray("fields").map { value ->
                val field = value.asJsonObject
                EmergencyRecoveryField(EmergencyRecoveryFieldType.valueOf(field["type"].asString), field["value"].asString)
            }
            val entry = WalletEmergencyRecoveryReader(preferences).read().single()
            assertEquals(context, key, entry.storageKey)
            assertEquals(context, vector["walletId"].asLong, entry.walletId)
            assertEquals(context, EmergencyRecoveryKeyKind.valueOf(vector["kind"].asString), entry.kind)
            assertEquals(context, EmergencyRecoveryState.ACTIVE, entry.state)
            assertTrue(context, entry.readable)
            assertEquals(context, expected, entry.fields)
            assertEquals(context, stored, preferences.getDecryptedString(key))
        }
    }
}
