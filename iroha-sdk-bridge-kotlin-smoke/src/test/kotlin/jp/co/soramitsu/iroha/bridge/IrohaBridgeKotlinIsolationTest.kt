package jp.co.soramitsu.iroha.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class IrohaBridgeKotlinIsolationTest {

    @Test
    fun kotlinTwoOneConsumerCompilesAndRunsWithoutSdkTypesInPublicApi() {
        val seed = ByteArray(32) { it.toByte() }
        val publicKey = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val authority = "testuﾛ1NeｱviV1aDbDｾNﾙZMｸAﾍﾐoﾍWc1j5cﾙyｲiﾕﾚcxﾘVﾛ6QG656"
        val assetDefinition = "61CtjvNd9T3THAR65GsMVHr82Bjc"
        val request = IrohaTransferRequest(
            IrohaTransferBridge.SUPPORTED_NETWORK,
            IrohaTransferBridge.SUPPORTED_CHAIN_ID,
            authority,
            authority,
            assetDefinition,
            "$assetDefinition#$authority",
            "testuﾛ1Npﾃﾕヱﾇq11pｳﾘ2ｱ5ﾇｦiCJKjRﾔzｷNMNﾆｹﾕPCｳﾙFvｵE9LBLB",
            "1.25",
            publicKey
        )

        val result = IrohaTransferBridge { 1_735_000_300_000L }
            .buildAndSignTransfer(request, seed)

        assertEquals(1, result.signedTransaction.first().toInt())
        assertTrue(result.transactionHashHex.matches(Regex("[0-9a-f]{64}")))
        assertTrue(seed.all { it == 0.toByte() })
    }

    @Test
    fun tairaBridgeRejectsNexusWalletSmokeMetadataBeforeSigning() {
        val publicKey = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val authority = "testuﾛ1NeｱviV1aDbDｾNﾙZMｸAﾍﾐoﾍWc1j5cﾙyｲiﾕﾚcxﾘVﾛ6QG656"
        val destination = "testuﾛ1Npﾃﾕヱﾇq11pｳﾘ2ｱ5ﾇｦiCJKjRﾔzｷNMNﾆｹﾕPCｳﾙFvｵE9LBLB"
        val assetDefinition = "61CtjvNd9T3THAR65GsMVHr82Bjc"
        val supplied = linkedMapOf(
            "evidence_role" to "wallet-smoke",
            "route_governance_action_hash" to
                "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "wallet_platform" to "android",
            "wallet_commit" to "0123456789abcdef0123456789abcdef01234567"
        )
        val request = IrohaTransferRequest(
            IrohaTransferBridge.SUPPORTED_NETWORK,
            IrohaTransferBridge.SUPPORTED_CHAIN_ID,
            authority,
            authority,
            assetDefinition,
            "$assetDefinition#$authority",
            destination,
            "1.25",
            publicKey,
            supplied
        )
        supplied.clear()

        assertEquals(
            listOf(
                "evidence_role",
                "route_governance_action_hash",
                "wallet_platform",
                "wallet_commit"
            ),
            request.transactionMetadata.keys.toList()
        )
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (request.transactionMetadata as MutableMap<String, String>)["attacker"] = "injected"
        }

        val clockCalled = AtomicBoolean(false)
        val seed = ByteArray(32) { it.toByte() }
        val error = assertThrows(IrohaBridgeException::class.java) {
            IrohaTransferBridge {
                clockCalled.set(true)
                1_735_000_300_000L
            }.buildAndSignTransfer(request, seed)
        }

        assertTrue(error.message.orEmpty().contains("Nexus-only"))
        assertFalse(clockCalled.get())
        assertTrue(seed.all { it == 0.toByte() })
    }
}
