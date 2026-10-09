package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.di.modules.irohaNoRedirectHttpClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Test

class IrohaNetworkModuleTest {

    @Test
    fun `iroha transport disables http and https redirects`() {
        val sharedClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val irohaClient = irohaNoRedirectHttpClient(sharedClient)

        assertFalse(irohaClient.followRedirects)
        assertFalse(irohaClient.followSslRedirects)
    }
}
