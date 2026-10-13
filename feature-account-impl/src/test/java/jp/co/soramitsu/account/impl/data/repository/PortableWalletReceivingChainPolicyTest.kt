package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PortableWalletReceivingChainPolicyTest {
    @Test
    fun `compiled receiving inventory exactly preserves bundled Substrate identities including disabled chains`() {
        val source = listOf(
            File("runtime/src/main/assets/local_chains.json"),
            File("../runtime/src/main/assets/local_chains.json"),
        ).single { it.isFile }.readBytes()
        val registry = JsonParser.parseString(source.toString(Charsets.UTF_8)).asJsonArray
        val substrate = registry.map { it.asJsonObject }.filter { it["ecosystem"]?.asString == "substrate" }
        val expected = substrate.map { "0x" + it["chainId"].asString }.sorted()
        val policy = PortableWalletReceivingChainPolicy.approvedGenesis
        assertEquals(expected, policy.map { it.id })
        assertEquals(87, policy.size)
        assertEquals(17, substrate.count { it["disabled"]?.asBoolean == true })
        assertEquals(
            setOf(PortableWalletChainSigningProof.IdentityKind.SUBSTRATE),
            policy.map { it.identityKind }.toSet(),
        )
        assertEquals(
            PortableWalletReceivingChainPolicy.SOURCE_SHA256,
            MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) },
        )
    }
}
