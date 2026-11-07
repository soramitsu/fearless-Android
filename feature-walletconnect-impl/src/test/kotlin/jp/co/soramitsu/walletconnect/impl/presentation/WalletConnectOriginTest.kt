package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resources.ResourceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletConnectOriginTest {
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } answers { "s${firstArg<Int>()}" }
        every { getString(any(), *anyVararg()) } answers { "s${firstArg<Int>()}:" + secondArg<Array<Any>>().joinToString(",") }
    }

    @Test
    fun `a verified origin shows that origin and no warning`() {
        val context = WalletConnectVerifyContext(WalletConnectValidation.VALID, "https://app.example.org", "https://app.example.org/", false)

        assertEquals("app.example.org", context.displayedHost)
        assertNull(context.originWarning(resources))
    }

    @Test
    fun `a mismatching origin shows the real origin and names both sites`() {
        val context = WalletConnectVerifyContext(WalletConnectValidation.INVALID, "https://evil.example", "https://app.example.org", false)

        assertEquals("evil.example", context.displayedHost)
        assertEquals("s${R.string.walletconnect_origin_mismatch}:evil.example,app.example.org", context.originWarning(resources))
    }

    @Test
    fun `an unverified dApp shows its claimed site and the unverified warning`() {
        val context = WalletConnectVerifyContext.unknown("https://app.example.org/")

        assertEquals("app.example.org", context.displayedHost)
        assertEquals("s${R.string.walletconnect_origin_unverified}", context.originWarning(resources))
    }

    @Test
    fun `the scam notice names the site Verify saw`() {
        val context = WalletConnectVerifyContext(WalletConnectValidation.UNKNOWN, "https://drainer.example", "https://app.example.org", true)

        assertTrue(context.isFlaggedAsScam)
        assertEquals("s${R.string.walletconnect_scam_blocked}:drainer.example", context.scamNotice(resources))
    }

    @Test
    fun `the claimed url loses only its scheme and trailing slash`() {
        assertEquals("app.example.org/path", WalletConnectMetadata("n", "d", "https://app.example.org/path/", emptyList()).dappUrl)
        assertEquals("plain", hostOf("plain"))
        assertNull((null as WalletConnectMetadata?).dappUrl)
    }
}
