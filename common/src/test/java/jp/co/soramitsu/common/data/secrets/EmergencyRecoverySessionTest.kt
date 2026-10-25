package jp.co.soramitsu.common.data.secrets

import android.content.SharedPreferences
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthentication
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EmergencyRecoverySessionTest {
    private class Fixture(keys: Boolean = true) {
        val values = linkedMapOf<String, String>().apply { if (keys) put(KEY, RAW) }
        val authenticator = mock(WalletEmergencyAuthenticator::class.java)
        val encryption = mock(EncryptionUtil::class.java)
        val shared = mock(SharedPreferences::class.java)
        var onInventory: () -> Unit = {}
        var onDecrypt: () -> Unit = {}
        var onAuthentication: () -> Unit = {}
        var authentication: WalletEmergencyAuthentication = WalletEmergencyAuthentication.Authenticated
        val session: WalletEmergencyRecoverySession

        init {
            whenever(shared.all).thenAnswer { onInventory(); LinkedHashMap(values) }
            whenever(shared.getString(any(), anyOrNull())).thenAnswer { values[it.getArgument<String>(0)] }
            whenever(encryption.decrypt(any())).thenAnswer { onDecrypt(); PLAIN }
            whenever(authenticator.authenticate(PIN)).thenAnswer { onAuthentication(); authentication }
            val reader = WalletEmergencyRecoveryReader(EncryptedPreferencesImpl(PreferencesImpl(shared), encryption))
            session = WalletEmergencyRecoverySession(authenticator, reader)
        }

        fun assertNoWrites() {
            verify(shared, never()).edit()
            assertEquals(if (values.isEmpty()) emptyMap() else mapOf(KEY to RAW), values)
        }

        fun assertNoKeyDecryption() = verify(encryption, never()).decrypt(any())
    }

    @Test
    fun freshSessionCannotListOrReadWithoutPinAuthentication() {
        val fixture = Fixture()
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.listPage() } }
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        verify(fixture.shared, never()).all
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun acceptedPinRevealsMetadataBeforeOneExplicitCurrentKey() = runBlocking {
        val fixture = Fixture()
        val result = fixture.session.unlock(PIN)
        assertTrue(result is EmergencyRecoveryAccessResult.Page)
        assertEquals(KEY, (result as EmergencyRecoveryAccessResult.Page).page.keys.single().storageKey)
        fixture.assertNoKeyDecryption()
        val entry = fixture.session.readKey(KEY)
        assertEquals(listOf(EmergencyRecoveryField(EmergencyRecoveryFieldType.STORED_DATA, PLAIN)), entry.fields)
        verify(fixture.encryption).decrypt(RAW)
        fixture.assertNoWrites()
    }

    @Test
    fun wrongLockedAndUnavailablePinNeverAuthorizeSecretRead() = runBlocking {
        for (refusal in listOf(WalletEmergencyAuthentication.WrongPin,
            WalletEmergencyAuthentication.LockedUntil(12345), WalletEmergencyAuthentication.Unavailable)) {
            val fixture = Fixture()
            fixture.authentication = refusal
            assertEquals(EmergencyRecoveryAccessResult.Refused(refusal), fixture.session.unlock(PIN))
            assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
            fixture.assertNoKeyDecryption()
            fixture.assertNoWrites()
        }
    }

    @Test
    fun unavailablePinAndEmptyInventoryDoesNotCreateAuthorization() = runBlocking {
        val fixture = Fixture(keys = false)
        fixture.authentication = WalletEmergencyAuthentication.Unavailable
        assertEquals(EmergencyRecoveryAccessResult.EmptyWithoutAuthentication, fixture.session.unlock(PIN))
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.listPage() } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun revocationRequiresNewPinAndARecreatedSessionStartsUnauthenticated() = runBlocking {
        val fixture = Fixture()
        fixture.session.unlock(PIN)
        fixture.session.revoke()
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { Fixture().session.readKey(KEY) } }
        fixture.assertNoKeyDecryption()
        assertTrue(fixture.session.unlock(PIN) is EmergencyRecoveryAccessResult.Page)
        assertEquals(KEY, fixture.session.readKey(KEY).storageKey)
        fixture.assertNoWrites()
    }

    @Test
    fun revocationDuringPinCheckCannotAuthorizeItsLateResult() {
        val fixture = Fixture()
        fixture.onAuthentication = { fixture.session.revoke() }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.session.unlock(PIN) } }
        verify(fixture.shared, never()).all
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun revocationDuringInitialInventoryCannotAuthorizeOrPublishPage() {
        val fixture = Fixture()
        fixture.onInventory = { fixture.session.revoke() }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.session.unlock(PIN) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun revocationDuringNextPagePreventsItsReturn() = runBlocking {
        val fixture = Fixture()
        fixture.session.unlock(PIN)
        fixture.onInventory = { fixture.session.revoke() }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.session.listPage() } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun revocationDuringSelectedDecryptPreventsReturningSecret() = runBlocking {
        val fixture = Fixture()
        fixture.session.unlock(PIN)
        fixture.onDecrypt = { fixture.session.revoke() }
        assertThrows(CancellationException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        verify(fixture.encryption).decrypt(RAW)
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        fixture.assertNoWrites()
    }

    @Test
    fun cancellationBeforeUnlockDoesNotCallAuthenticator() {
        val fixture = Fixture()
        assertThrows(CancellationException::class.java) {
            runBlocking { cancel("public cancelled control"); fixture.session.unlock(PIN) }
        }
        verify(fixture.authenticator, never()).authenticate(any())
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun inventoryFailureAfterPinDoesNotAuthorizeOrBecomeEmpty() {
        val fixture = Fixture()
        fixture.onInventory = { throw IllegalStateException("public inventory failed") }
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.unlock(PIN) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { fixture.session.readKey(KEY) } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    @Test
    fun fatalAuthenticationFailureIsNotConvertedToEmptyOrRefused() {
        val fixture = Fixture()
        fixture.onAuthentication = { throw AssertionError("public fatal control") }
        assertThrows(AssertionError::class.java) { runBlocking { fixture.session.unlock(PIN) } }
        fixture.assertNoKeyDecryption()
        fixture.assertNoWrites()
    }

    private companion object {
        const val KEY = "42:SUBSTRATE_SECRETS"
        const val RAW = "public ciphertext fixture"
        const val PLAIN = "public undecodable exact key fixture"
        const val PIN = "123456"
    }
}
