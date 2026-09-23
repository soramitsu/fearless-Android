package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ChainActivationPolicyTest {

    @Test
    fun `stale crowdloan metadata alone cannot activate a network`() {
        val legacyOnly = mock(Chain::class.java).also { chain ->
            `when`(chain.id).thenReturn("legacy-crowdloan-chain")
            `when`(chain.rank).thenReturn(null)
            `when`(chain.hasCrowdloans).thenReturn(true)
            `when`(chain.assets).thenReturn(emptyList())
            `when`(chain.identityChain).thenReturn(null)
            `when`(chain.nodes).thenReturn(
                listOf(ChainNode("wss://legacy.example", "Legacy", false, true))
            )
        }

        val selected = selectChainsForBackgroundSync(
            chains = listOf(legacyOnly),
            enabledChainIds = emptySet()
        )

        assertTrue(selected.isEmpty())
    }
}
