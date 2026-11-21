package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainMetadataHashTest {

    @Test
    fun `the System pallet states the runtime version and address prefix of every fixture`() {
        MetadataHashFixtures.vectors.chains.forEach { chain ->
            val metadata = MetadataHashFixtures.metadata(chain)
            val version = ChainMetadataHash.runtimeVersion(metadata)
            assertEquals(chain.name, chain.specName, version.specName)
            assertEquals(chain.name, chain.specVersion, version.specVersion)
            assertEquals(chain.name, chain.transactionVersion, version.transactionVersion)
            assertEquals(chain.name, chain.base58Prefix, ChainMetadataHash.ss58Prefix(metadata))
            assertTrue(chain.name, ChainMetadataHash.advertises(metadata))
        }
    }

    @Test
    fun `extra information takes decimals and symbol from the caller`() {
        val chain = MetadataHashFixtures.chain("Kusama")
        val info = ChainMetadataHash.extraInfo(MetadataHashFixtures.metadata(chain), 12, "KSM")
        assertEquals(2, info.base58Prefix)
        assertEquals("kusama", info.specName)
        assertEquals(12, info.decimals)
        assertEquals("KSM", info.tokenSymbol)
    }
}
