package jp.co.soramitsu.runtime.multiNetwork.runtime.metadatahash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataHashExemptionsTest {

    private val pendulum = "5d3c298622d5634ed019bf61ea4b71655030015bde9beb0d6a24743714462c86"
    private val avn = "8b5c955b5c8fd7112562327e3859473df4e3dff49bd72a113dbb668d2cfa20d7"

    @Test
    fun `only the reviewed networks without an embedded hash are exempt`() {
        assertNotNull(MetadataHashExemptions.Default.reasonFor(pendulum))
        assertNotNull(MetadataHashExemptions.Default.reasonFor(avn))
        listOf(
            "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3", // Polkadot
            "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe", // Kusama
            "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e", // Westend
            "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f", // Polkadot Asset Hub
            "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a" // Kusama Asset Hub
        ).forEach { assertNull(it, MetadataHashExemptions.Default.reasonFor(it)) }
    }

    @Test
    fun `chain ids match with or without a prefix and in any case`() {
        val exemptions = MetadataHashExemptions(mapOf(pendulum to "reason"))
        assertEquals("reason", exemptions.reasonFor("0x" + pendulum.uppercase()))
        assertNull(exemptions.reasonFor(avn))
    }
}
