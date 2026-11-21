package jp.co.soramitsu.walletconnect.impl.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Vectors from the official `@walletconnect/relay-auth` (`generateKeyPair(seed)`, `encodeIss`, `signJWT`). */
class WcClientIdentityTest {

    private val identity = WcClientIdentity(WcEncoding.unhex(SEED))

    @Test
    fun `client id is the did key of the ed25519 public key`() {
        assertEquals(CLIENT_ID, identity.clientId)
        assertEquals(CLIENT_ID, WcClientIdentity.didKey(identity.publicKey))
    }

    @Test
    fun `relay auth jwt matches the official SDK byte for byte`() {
        val jwt = identity.signJwt(
            audience = "wss://relay.walletconnect.org",
            subject = "ab".repeat(32),
            issuedAtSeconds = 1_700_000_000
        )

        assertEquals(JWT, jwt)
    }

    @Test
    fun `a seed of another length is refused`() {
        assertThrows(IllegalArgumentException::class.java) { WcClientIdentity(ByteArray(31)) }
    }

    private companion object {
        const val SEED = "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"
        const val CLIENT_ID = "did:key:z6MkneMkZqwqRiU5mJzSG3kDwzt9P8C59N4NGTfBLfSGE7c7"
        const val JWT = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9." +
            "eyJpc3MiOiJkaWQ6a2V5Ono2TWtuZU1rWnF3cVJpVTVtSnpTRzNrRHd6dDlQOEM1OU40TkdUZkJMZlNHRTdjNyIsInN1YiI6ImFiYWJhYmFiYWJhYmFiYWJh" +
                "YmFiYWJhYmFiYWJhYmFiYWJhYmFiYWJhYmFiYWJhYmFiYWJhYmFiYWJhYmFiYWIiLCJhdWQiOiJ3c3M6Ly9yZWxheS53YWxsZXRjb25uZWN0Lm9yZyIsImlh" +
                "dCI6MTcwMDAwMDAwMCwiZXhwIjoxNzAwMDg2NDAwfQ." +
            "YFn6OP9P_wGeu9hPcwFcfY6J8zVJxZDxwcTfMxTM2mf1lvpNN8zd0Bn4S5UvnyRjuAHSgnaFsP2S98ZJp0TKAg"
    }
}
