package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import com.google.gson.JsonParseException
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IrohaAssetDefinitionSpecJsonTest {

    private val gson = Gson()

    @Test
    fun `decodes only canonical integer scale tokens`() {
        assertEquals(9, gson.fromJson("{\"scale\":9}", IrohaAssetDefinitionSpec::class.java).scale)
        assertNull(gson.fromJson("{}", IrohaAssetDefinitionSpec::class.java).scale)
        assertNull(gson.fromJson("{\"scale\":null}", IrohaAssetDefinitionSpec::class.java).scale)

        listOf(
            "{\"scale\":9.0}",
            "{\"scale\":2.5}",
            "{\"scale\":1e1}",
            "{\"scale\":\"9\"}",
            "{\"scale\":true}",
            "{\"scale\":{}}",
            "{\"scale\":2147483648}"
        ).forEach { json ->
            assertThrows(JsonParseException::class.java) {
                gson.fromJson(json, IrohaAssetDefinitionSpec::class.java)
            }
        }
    }
}
