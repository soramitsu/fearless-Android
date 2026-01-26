package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.runtime.multiNetwork.chain.solana.SolanaChainDefinition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemecoinTaggerTest {

    @Test
    fun `marks listed solana assets as memecoins`() {
        assertTrue(MemecoinTagger.isMemecoin(SolanaChainDefinition.CHAIN_ID, "BONK"))
    }

    @Test
    fun `does not mark sol as memecoin`() {
        assertFalse(MemecoinTagger.isMemecoin(SolanaChainDefinition.CHAIN_ID, "SOL"))
    }

    @Test
    fun `ignores other chains`() {
        assertFalse(MemecoinTagger.isMemecoin("polkadot", "BONK"))
    }
}
