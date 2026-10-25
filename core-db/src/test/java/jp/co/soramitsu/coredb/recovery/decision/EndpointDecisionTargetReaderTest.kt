package jp.co.soramitsu.coredb.recovery.decision

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.*
import org.junit.Test

class EndpointDecisionTargetReaderTest {
    @Test fun exactSolanaRegistryIdsAndAliasesBindTheirOwnGenesis() {
        listOf(UniversalWalletRegistry.solanaMainnet to false, UniversalWalletRegistry.solanaDevnet to true)
            .forEach { (network, testnet) ->
                for (id in listOf(network.id, network.chainId)) {
                    val target = EndpointDecisionTargetReader.capture(id, "Substrate", false, false, testnet, "SOLANA")
                    assertEquals(EndpointDecisionTarget.Family.SOLANA, target.family)
                    assertEquals(network.genesisHash, target.expectedGenesis)
                }
            }
    }

    @Test fun unknownSolanaIdWrongTestnetOrCompatibilityFlagsNeverInventTrustedIdentity() {
        val id = UniversalWalletRegistry.solanaMainnet.id
        val candidates = listOf(
            EndpointDecisionTargetReader.capture("arbitrary-solana", "Substrate", false, false, false, "SOLANA"),
            EndpointDecisionTargetReader.capture(id, "Substrate", false, false, true, "SOLANA"),
            EndpointDecisionTargetReader.capture(id, "Substrate", true, false, false, "SOLANA"),
            EndpointDecisionTargetReader.capture(id, "Ethereum", false, false, false, "SOLANA"),
            EndpointDecisionTargetReader.capture(id, "Substrate", false, false, false, null)
        )
        candidates.forEach { assertEquals(EndpointDecisionTarget.Family.UNSUPPORTED, it.family); assertNull(it.expectedGenesis) }
    }

    @Test fun substrateGenesisComesFromExactHexCatalogIdAndEthereumChainIsUnsupported() {
        val id = "ABCDEF01".repeat(8)
        for (prefix in listOf("", "0x")) {
            val target = EndpointDecisionTargetReader.capture(prefix + id, "Substrate", true, false, false, null)
            assertEquals(EndpointDecisionTarget.Family.SUBSTRATE, target.family)
            assertEquals("0x" + id.lowercase(), target.expectedGenesis)
        }
        val unsupported = EndpointDecisionTargetReader.capture(id, "Substrate", true, true, false, null)
        assertEquals(EndpointDecisionTarget.Family.UNSUPPORTED, unsupported.family)
    }

    @Test fun everyIdentityFieldChangesFingerprintWithoutExposingValuesInDebug() {
        val id = "1".repeat(64)
        val base = EndpointDecisionTargetReader.capture(id, "Substrate", false, false, false, null)
        val variants = listOf(
            EndpointDecisionTargetReader.capture("2".repeat(64), "Substrate", false, false, false, null),
            EndpointDecisionTargetReader.capture(id, "Other", false, false, false, null),
            EndpointDecisionTargetReader.capture(id, "Substrate", true, false, false, null),
            EndpointDecisionTargetReader.capture(id, "Substrate", false, true, false, null),
            EndpointDecisionTargetReader.capture(id, "Substrate", false, false, true, null),
            EndpointDecisionTargetReader.capture(id, "Substrate", false, false, false, "")
        )
        variants.forEach { assertNotEquals(base.identityFingerprint, it.identityFingerprint) }
        assertFalse(base.toString().contains(id))
    }
}
