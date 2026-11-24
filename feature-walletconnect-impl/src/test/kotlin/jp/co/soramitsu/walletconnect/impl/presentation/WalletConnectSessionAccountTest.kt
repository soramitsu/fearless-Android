package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectSessionAccountTest {

    private val evmAddress = "0x8ba1f109551bd432803012645ac136ddd64dba72"
    private val checksummedEvmAddress = "0x8ba1f109551bD432803012645Ac136ddd64DBA72"
    private val substrateAccountId = ByteArray(32) { (it + 7).toByte() }
    private val polkadotAddress = SS58Encoder.encode(substrateAccountId, 0)
    private val genericAddress = SS58Encoder.encode(substrateAccountId, 42)

    @Test
    fun `approved addresses are read for exactly the requested network`() {
        val session = session(
            "eip155:1:$evmAddress",
            "eip155:10:0x1111111111111111111111111111111111111111",
            "polkadot:91b171bb158e2d3848fa23a9f1c25182:$polkadotAddress"
        )

        assertEquals(listOf(evmAddress), session.approvedAddresses("eip155:1"))
        assertEquals(listOf(polkadotAddress), session.approvedAddresses(polkadotChain.caip2id))
        assertTrue(session.approvedAddresses("eip155:56").isEmpty())
    }

    @Test
    fun `evm account is approved regardless of hex case`() {
        val session = session("eip155:1:$evmAddress")

        assertTrue(session.approvesAccount("eip155:1", ethereumChain.accountIdOf(checksummedEvmAddress), ethereumChain::accountIdOf))
        assertTrue(session.approvesAccount("eip155:1", evmAddress.fromHex(), ethereumChain::accountIdOf))
    }

    @Test
    fun `substrate account is approved in any SS58 format`() {
        val session = session("polkadot:91b171bb158e2d3848fa23a9f1c25182:$polkadotAddress")

        assertTrue(session.approvesAccount(polkadotChain.caip2id, polkadotChain.accountIdOf(genericAddress), polkadotChain::accountIdOf))
    }

    @Test
    fun `account approved on another network or another account is refused`() {
        val session = session("eip155:56:$evmAddress", "eip155:1:0x1111111111111111111111111111111111111111")

        assertFalse(session.approvesAccount("eip155:1", evmAddress.fromHex(), ethereumChain::accountIdOf))
        assertFalse(session.approvesAccount("eip155:10", evmAddress.fromHex(), ethereumChain::accountIdOf))
    }

    @Test
    fun `malformed approved address is ignored`() {
        val session = session("polkadot:91b171bb158e2d3848fa23a9f1c25182:not-an-address")

        assertFalse(session.approvesAccount(polkadotChain.caip2id, substrateAccountId, polkadotChain::accountIdOf))
    }

    @Test
    fun `methods sign only on their own kind of network`() {
        val bitcoin = testChain("bitcoin", Ecosystem.Substrate).copy(
            externalApi = Chain.ExternalApi(
                staking = null,
                history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.BITCOIN, "https://example.invalid"),
                crowdloans = null
            )
        )

        assertTrue(WalletConnectMethod.PolkadotSignMessage.isAvailableOn(polkadotChain))
        assertTrue(WalletConnectMethod.PolkadotSignTransaction.isAvailableOn(polkadotChain))
        assertTrue(WalletConnectMethod.PolkadotSignMessage.isAvailableOn(moonbeamChain))
        assertFalse(WalletConnectMethod.PolkadotSignTransaction.isAvailableOn(moonbeamChain))
        assertFalse(WalletConnectMethod.PolkadotSignMessage.isAvailableOn(ethereumChain))
        assertFalse(WalletConnectMethod.PolkadotSignMessage.isAvailableOn(bitcoin))

        assertTrue(WalletConnectMethod.EthereumPersonalSign.isAvailableOn(ethereumChain))
        assertFalse(WalletConnectMethod.EthereumPersonalSign.isAvailableOn(polkadotChain))
        assertFalse(WalletConnectMethod.EthereumSendTransaction.isAvailableOn(moonbeamChain))
    }
}
