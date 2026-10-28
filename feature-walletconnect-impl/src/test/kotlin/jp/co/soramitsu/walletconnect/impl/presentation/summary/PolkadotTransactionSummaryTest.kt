package jp.co.soramitsu.walletconnect.impl.presentation.summary

import jp.co.soramitsu.walletconnect.impl.presentation.KUSAMA_GENESIS
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes
import jp.co.soramitsu.walletconnect.impl.presentation.ParityRuntimes.cases
import jp.co.soramitsu.walletconnect.impl.presentation.PolkadotRefusal
import jp.co.soramitsu.walletconnect.impl.presentation.caip2id
import jp.co.soramitsu.walletconnect.impl.presentation.polkadotChain
import jp.co.soramitsu.walletconnect.impl.presentation.sessionRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The preview runs the signer's own checks, so it shows a refusal before the user taps Sign. */
class PolkadotTransactionSummaryTest {
    private val runtime = ParityRuntimes.runtime("polkadot")
    private val withRuntime = WalletConnectRequestDecoder { runtime }
    private val offline = WalletConnectRequestDecoder { null }

    @Test
    fun `a polkadot js transfer is decoded and signable`() {
        val summary = summary(withRuntime, payload())

        assertNull(summary.refusal)
        assertEquals("Balances.transfer_keep_alive", summary.call?.name)
    }

    @Test
    fun `another network and an immortal era are refused even without the runtime`() {
        val kusama = JsonPrimitive("0x$KUSAMA_GENESIS")
        assertEquals(PolkadotRefusal.OtherNetwork, summary(offline, payload { put("genesisHash", kusama) }).refusal)
        assertEquals(PolkadotRefusal.NeverExpires, summary(offline, payload { put("era", JsonPrimitive("0x00")) }).refusal)
        assertNull(summary(offline, payload()).refusal)
    }

    @Test
    fun `runtime checks refuse what the signer refuses`() {
        val olderRuntime = JsonPrimitive("0x001e9807")
        assertEquals(PolkadotRefusal.OtherRuntime, summary(withRuntime, payload { put("specVersion", olderRuntime) }).refusal)
        assertEquals(
            PolkadotRefusal.UndecodableCall,
            summary(withRuntime, payload { put("method", JsonPrimitive("0xff00")) }).refusal
        )
    }

    private fun payload(
        change: MutableMap<String, kotlinx.serialization.json.JsonElement>.() -> Unit = {}
    ): JsonObject {
        val chain = ParityRuntimes.chain("polkadot")
        val case = chain.cases()[1]
        val fields = ParityRuntimes.signerPayload(chain, case, ParityRuntimes.callOf(runtime, case), "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5")
            .toMutableMap()
        fields.change()
        return JsonObject(fields)
    }

    private fun summary(decoder: WalletConnectRequestDecoder, payload: JsonObject) = runBlocking {
        decoder.summary(
            polkadotChain,
            sessionRequest(
                id = 1,
                method = "polkadot_signTransaction",
                params = buildJsonObject {
                    put("address", "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5")
                    put("transactionPayload", payload)
                }.toString(),
                chainId = polkadotChain.caip2id
            )
        ) as WalletConnectRequestSummary.PolkadotTransaction
    }
}
