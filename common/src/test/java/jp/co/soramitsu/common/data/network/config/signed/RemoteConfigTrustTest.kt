package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigTrustTest {

    private val base = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/"

    @Test
    fun `configuration files below the published prefix are signed paths`() {
        assertEquals("production/chains/v13/chains.json", RemoteConfigTrust.signedPathOf((base + "production/chains/v13/chains.json").toHttpUrl()))
        assertEquals("production/appConfigs/feature_toggle", RemoteConfigTrust.signedPathOf((base + "production/appConfigs/feature_toggle").toHttpUrl()))
        assertEquals(
            "production/appConfigs/onboarding/mobile v2.json",
            RemoteConfigTrust.signedPathOf((base + "production/appConfigs/onboarding/mobile%20v2.json").toHttpUrl())
        )
    }

    @Test
    fun `images, the manifest, other hosts, repositories and schemes are not`() {
        listOf(
            base + "assets/icons/tokens/coloured/DOT.svg",
            base + "assets/icons/FW%20icon%20128.png",
            base + RemoteConfigTrust.MANIFEST_PATH,
            "https://raw.githubusercontent.com/soramitsu/fearless-iOS/master/remote-config/chains.json",
            "https://raw.githubusercontent.com/soramitsu/fearless-Android/develop/remote-config/chains.json",
            "http://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/chains.json",
            "https://raw.githubusercontent.com:8443/soramitsu/fearless-Android/master/remote-config/chains.json",
            "https://example.com/soramitsu/fearless-Android/master/remote-config/chains.json",
            base
        ).forEach { url -> assertNull(url, RemoteConfigTrust.signedPathOf(url.toHttpUrl())) }
    }

    @Test
    fun `signed paths have no empty or traversing segments`() {
        assertTrue(RemoteConfigTrust.isSignedPath("production/polkaswapSettings.json"))
        listOf("", "a//b.json", "../a.json", "a/./b.json", "a/b.PNG", RemoteConfigTrust.MANIFEST_PATH).forEach { path ->
            assertFalse(path, RemoteConfigTrust.isSignedPath(path))
        }
    }

    @Test
    fun `the embedded public key is the 32 byte Ed25519 key`() {
        assertEquals(32, RemoteConfigTrust.publicKey.size)
        assertEquals(TestManifests.sha256(RemoteConfigTrust.publicKey).take(16), RemoteConfigTrust.KEY_ID)
    }
}
