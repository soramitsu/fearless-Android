package jp.co.soramitsu.irohaconnect.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrohaConnectLinksTest {

    @Test
    fun shouldRecogniseConnectLinks_whenBothSchemesAreUsed() {
        assertTrue(IrohaConnectLinks.isConnectLink("iroha://connect?sid=x"))
        assertTrue(IrohaConnectLinks.isConnectLink("irohaconnect://connect?sid=x"))
    }

    @Test
    fun shouldIgnoreOtherText_whenItIsNotAConnectInvitation() {
        assertFalse(IrohaConnectLinks.isConnectLink(null))
        assertFalse(IrohaConnectLinks.isConnectLink(""))
        assertFalse(IrohaConnectLinks.isConnectLink("wc:abc@2?relay-protocol=irn"))
        assertFalse(IrohaConnectLinks.isConnectLink(" iroha://connect?sid=x"))
        assertFalse(IrohaConnectLinks.isConnectLink("iroha://connect/path?sid=x"))
        assertFalse(IrohaConnectLinks.isConnectLink("https://connect?sid=x"))
    }
}
