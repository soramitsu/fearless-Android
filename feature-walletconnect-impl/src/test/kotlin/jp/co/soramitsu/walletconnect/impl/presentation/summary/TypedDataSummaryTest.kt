package jp.co.soramitsu.walletconnect.impl.presentation.summary

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Hash
import org.web3j.crypto.StructuredDataEncoder
import java.math.BigInteger

class TypedDataSummaryTest {
    @Test
    fun `EIP2612 permit decodes domain and exact allowance from signer encoding`() {
        val json = typed("Permit", EIP2612, """{"owner":"$OWNER","spender":"$SPENDER","value":"123456789012345678901","nonce":4,"deadline":1800000000}""")
        val summary = requireNotNull(TypedDataSummaries.summary(json))
        assertEquals(TypedDataDomain("USD Coin", "2", BigInteger.ONE, checksumAddress(TOKEN)), summary.domain)
        assertEquals(TypedDataPermit.TokenAllowance(checksumAddress(TOKEN), checksumAddress(SPENDER), BigInteger("123456789012345678901"), BigInteger("1800000000")), summary.permit)
        assertTrue(summary.isComplete)
        val view = requireNotNull(TypedDataView.parse(json))
        val message = requireNotNull(view.message)
        val encoder = StructuredDataEncoder(json)
        assertArrayEquals(encoder.hashStructuredData(), Hash.sha3(byteArrayOf(0x19, 0x01) + Hash.sha3(view.domain!!.encoded) + Hash.sha3(message.encoded)))
        assertEquals(checksumAddress(SPENDER), message.address("spender"))
        assertNull(message.address("value"))
        assertNull(message.uint("owner"))
        assertNull(message.bool("owner"))
        assertNull(message.string("owner"))
        assertNull(message.struct("owner"))
        assertNull(message.structs("owner"))
    }

    @Test
    fun `DAI allowed gives unlimited allowance with zero expiry`() {
        val fields = """"Permit":[{"name":"holder","type":"address"},{"name":"spender","type":"address"},{"name":"nonce","type":"uint256"},{"name":"expiry","type":"uint256"},{"name":"allowed","type":"bool"}]"""
        for (allowed in listOf(true, false)) {
            val summary = TypedDataSummaries.summary(typed("Permit", fields, """{"holder":"$OWNER","spender":"$SPENDER","nonce":0,"expiry":0,"allowed":$allowed}"""))!!
            val permit = summary.permit as TypedDataPermit.TokenAllowance
            assertEquals(allowed, permit.amount.isUnlimitedAllowance())
            assertNull(permit.deadline)
        }
    }

    @Test
    fun `PermitSingle decodes uint160 amount and uint48 expiration`() {
        val fields = """"PermitSingle":[{"name":"details","type":"PermitDetails"},{"name":"spender","type":"address"},{"name":"sigDeadline","type":"uint256"}],$DETAIL_TYPES"""
        val json = typed("PermitSingle", fields, """{"details":$DETAIL,"spender":"$SPENDER","sigDeadline":1800000000}""")
        val summary = TypedDataSummaries.summary(json)!!
        val permit = summary.permit as TypedDataPermit.Permit2Allowance
        assertEquals(checksumAddress(SPENDER), permit.spender)
        assertEquals(listOf(TypedDataPermit.Permit2Allowance.Allowance(checksumAddress(TOKEN), BigInteger("1000001"), BigInteger("1700000000"))), permit.allowances)
        assertEquals(BigInteger("1800000000"), permit.signatureDeadline)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `PermitBatch keeps every nested allowance in encoded order`() {
        val fields = """"PermitBatch":[{"name":"details","type":"PermitDetails[]"},{"name":"spender","type":"address"},{"name":"sigDeadline","type":"uint256"}],$DETAIL_TYPES"""
        val json = typed("PermitBatch", fields, """{"details":[$DETAIL,${DETAIL.replace("1000001", "2000002")}],"spender":"$SPENDER","sigDeadline":1800000000}""")
        val permit = TypedDataSummaries.summary(json)!!.permit as TypedDataPermit.Permit2Allowance
        assertEquals(listOf(BigInteger("1000001"), BigInteger("2000002")), permit.allowances.map { it.amount })
    }

    @Test
    fun `Permit2 transfers and batches decode token amount and spender`() {
        for (batch in listOf(false, true)) {
            val primary = if (batch) "PermitBatchTransferFrom" else "PermitTransferFrom"
            val tokenType = if (batch) "TokenPermissions[]" else "TokenPermissions"
            val fields = transferFields(primary, tokenType)
            val permissions = """{"token":"$TOKEN","amount":"3000003"}"""
            val json = typed(primary, fields, """{"permitted":${if (batch) "[$permissions]" else permissions},"spender":"$SPENDER","nonce":2,"deadline":1800000000}""")
            val summary = TypedDataSummaries.summary(json)!!
            val permit = summary.permit as TypedDataPermit.Permit2Transfer
            assertEquals(checksumAddress(SPENDER), permit.spender)
            assertEquals(listOf(TypedDataPermit.Permit2Transfer.Transfer(checksumAddress(TOKEN), BigInteger("3000003"))), permit.transfers)
            assertFalse(permit.hasWitness)
            assertTrue(summary.isComplete)
        }
    }

    @Test
    fun `witness order data always remains available as raw fallback`() {
        val fields = transferFields("PermitWitnessTransferFrom", "TokenPermissions", """,{"name":"witness","type":"Order"}""") +
            """, "Order":[{"name":"recipient","type":"address"},{"name":"minimum","type":"uint256"}]"""
        val json = typed("PermitWitnessTransferFrom", fields, """{"permitted":{"token":"$TOKEN","amount":9},"spender":"$SPENDER","nonce":2,"deadline":1800000000,"witness":{"recipient":"$OWNER","minimum":1}}""")
        val summary = TypedDataSummaries.summary(json)!!
        assertTrue((summary.permit as TypedDataPermit.Permit2Transfer).hasWitness)
        assertFalse(summary.isComplete)
    }

    @Test
    fun `unknown fields or incompatible permit types cannot hide raw data`() {
        val message = """{"owner":"$OWNER","spender":"$SPENDER","value":9,"nonce":4,"deadline":1800000000}"""
        val extraFields = EIP2612.dropLast(1) + """,{"name":"operator","type":"address"}]"""
        val extraMessage = message.dropLast(1) + """, "operator":"$OWNER"}"""
        assertNull(TypedDataSummaries.summary(typed("Permit", extraFields, extraMessage))!!.permit)
        assertNull(TypedDataSummaries.summary(typed("Permit", EIP2612.replace("\"value\",\"type\":\"uint256\"", "\"value\",\"type\":\"uint160\""), message))!!.permit)
        assertFalse(TypedDataSummaries.summary(typed("Permit", EIP2612, message.replace(",\"nonce\":4", "")))!!.isComplete)
    }

    @Test
    fun `unrepresented domain salt requires raw review`() {
        val json = typed("Permit", EIP2612, """{"owner":"$OWNER","spender":"$SPENDER","value":9,"nonce":4,"deadline":1800000000}""")
            .replace("{\"name\":\"verifyingContract\",\"type\":\"address\"}", "{\"name\":\"verifyingContract\",\"type\":\"address\"},{\"name\":\"salt\",\"type\":\"bytes32\"}")
            .replace("\"verifyingContract\":\"$TOKEN\"", "\"verifyingContract\":\"$TOKEN\",\"salt\":\"0x${"01".repeat(32)}\"")
        val summary = TypedDataSummaries.summary(json)!!
        assertFalse(summary.isComplete)
    }

    @Test
    fun `duplicate typed field names cannot collapse encoded words into one displayed row`() {
        val fields = EIP2612.dropLast(1) + """,{"name":"spender","type":"address"}]"""
        val summary = TypedDataSummaries.summary(
            typed(
                "Permit", fields,
            """{"owner":"$OWNER","spender":"$SPENDER","value":9,"nonce":4,"deadline":1800000000}"""
            )
        )!!
        assertFalse(summary.isComplete)
    }

    @Test
    fun `invalid typed JSON is refused`() {
        assertNull(TypedDataSummaries.summary("not json"))
        assertNull(TypedDataView.parse("{}"))
    }

    private fun transferFields(
        primary: String,
        permissionsType: String,
        extra: String = ""
    ) =
        """"$primary":[{"name":"permitted","type":"$permissionsType"},{"name":"spender","type":"address"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}$extra],"TokenPermissions":[{"name":"token","type":"address"},{"name":"amount","type":"uint256"}]"""

    private fun typed(
        primary: String,
        fields: String,
        message: String
    ) = """{
        "types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"},{"name":"verifyingContract","type":"address"}],$fields},
        "primaryType":"$primary","domain":{"name":"USD Coin","version":"2","chainId":1,"verifyingContract":"$TOKEN"},"message":$message
    }"""

    private companion object {
        const val OWNER = "0x1111111111111111111111111111111111111111"
        const val SPENDER = "0x2222222222222222222222222222222222222222"
        const val TOKEN = "0x3333333333333333333333333333333333333333"
        const val EIP2612 = """"Permit":[{"name":"owner","type":"address"},{"name":"spender","type":"address"},{"name":"value","type":"uint256"},{"name":"nonce","type":"uint256"},{"name":"deadline","type":"uint256"}]"""
        const val DETAIL_TYPES = """"PermitDetails":[{"name":"token","type":"address"},{"name":"amount","type":"uint160"},{"name":"expiration","type":"uint48"},{"name":"nonce","type":"uint48"}]"""
        const val DETAIL = """{"token":"$TOKEN","amount":"1000001","expiration":1700000000,"nonce":1}"""
    }
}
