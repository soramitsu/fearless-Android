package jp.co.soramitsu.common.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensitiveClipboardTest {
    private class FakePort(var hidden: Boolean = false) : SensitiveClipboard.Port {
        var text: String? = null
        var sensitiveCopies = 0
        override fun copySensitive(label: String, text: String) { this.text = text; sensitiveCopies++ }
        override fun currentText(): String? = if (hidden) null else text
        override fun clear() { text = null }
    }

    private val timers = mutableListOf<Pair<Long, () -> Unit>>()
    private fun clipboard(port: FakePort) = SensitiveClipboard(port) { delay, action -> timers += delay to action }

    @Test fun `a secret is copied as sensitive and cleared after a minute`() {
        val port = FakePort()
        clipboard(port).copy("seed", "secret-1")
        assertEquals("secret-1", port.text)
        assertEquals(1, port.sensitiveCopies)
        assertEquals(SensitiveClipboard.LIFETIME_MS, timers.single().first)
        timers.single().second()
        assertNull(port.text)
    }

    @Test fun `something the user copied since is left alone`() {
        val port = FakePort()
        clipboard(port).copy("seed", "secret-1")
        port.text = "an address the user copied later"
        timers.single().second()
        assertEquals("an address the user copied later", port.text)
    }

    @Test fun `a hidden clipboard is cleared anyway`() {
        val port = FakePort()
        clipboard(port).copy("seed", "secret-1")
        port.hidden = true
        timers.single().second()
        assertNull(port.text)
    }

    @Test fun `only the newest secret copy owns the timer`() {
        val port = FakePort()
        val clipboard = clipboard(port)
        clipboard.copy("seed", "secret-1")
        clipboard.copy("seed", "secret-2")
        timers[0].second()
        assertEquals("The first timer must not clear the newer secret", "secret-2", port.text)
        timers[1].second()
        assertNull(port.text)
    }
}
