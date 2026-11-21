package jp.co.soramitsu.backup.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GoogleOAuthConfigTest {

    @Test
    fun `default client is the backup project's iOS client with its reversed-id redirect`() {
        val config = GoogleOAuthConfig.create("")

        assertEquals(GoogleOAuthConfig.DEFAULT_CLIENT_ID, config.clientId)
        assertEquals("com.googleusercontent.apps.621183184145-jla1mrbl3soobt85btt2nebon8s3d30b", config.redirectScheme)
        assertEquals(
            "com.googleusercontent.apps.621183184145-jla1mrbl3soobt85btt2nebon8s3d30b:/oauth2redirect",
            config.redirectUri
        )
    }

    @Test
    fun `another client of the backup project derives its own redirect`() {
        val config = GoogleOAuthConfig.create(" 621183184145-abcdef0123.apps.googleusercontent.com ")

        assertEquals("621183184145-abcdef0123.apps.googleusercontent.com", config.clientId)
        assertEquals("com.googleusercontent.apps.621183184145-abcdef0123:/oauth2redirect", config.redirectUri)
    }

    @Test
    fun `clients of other projects are refused so existing backups never look gone`() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleOAuthConfig.create("123456789012-abcdef0123.apps.googleusercontent.com")
        }
    }

    @Test
    fun `malformed client ids are refused`() {
        listOf(
            "621183184145-ABC.apps.googleusercontent.com",
            "621183184145-abc.apps.googleusercontent.com.evil",
            "621183184145-abc",
            "com.googleusercontent.apps.621183184145-abc",
            "621183184145-abc.apps.googleusercontent.com/x"
        ).forEach { candidate ->
            assertThrows(candidate, IllegalArgumentException::class.java) { GoogleOAuthConfig.create(candidate) }
        }
    }
}
