package jp.co.soramitsu.account.impl.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountAlreadyExistsException
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountRepositoryDelegateRoutingTest {

    private val substrateOrEvm = mockk<SubstrateOrEvmAccountRepository>()
    private val ton = mockk<TonAccountRepository>()
    private val delegate = AccountRepositoryDelegate(substrateOrEvm, ton)
    private val words = List(24) { "word$it" }.joinToString(" ")

    @Test
    fun eachPayloadKindIsCreatedByItsOwnRepository(): Unit = runBlocking {
        val substrate = AddAccountPayload.SubstrateOrEvm("Main", words, CryptoType.SR25519, "", "", null, true)
        val tonPayload = AddAccountPayload.Ton("TON", words, false)
        val additional = AddAccountPayload.AdditionalEvm(4L, "EVM", words, "m/44'/60'/0'/0/1", true)
        coEvery { substrateOrEvm.create(substrate) } returns 1L
        coEvery { ton.create(tonPayload) } returns 2L
        coEvery { substrateOrEvm.createAdditional(additional) } returns 3L
        coEvery { substrateOrEvm.createFromBackup(substrate, "0xkey") } returns 4L

        assertEquals(1L, delegate.create(substrate))
        assertEquals(2L, delegate.create(tonPayload))
        assertEquals(3L, delegate.create(additional))
        assertEquals(4L, delegate.createFromBackup(substrate, "0xkey"))
    }

    @Test
    fun tonWalletIsCreatedAsASelectedTonOnlyWalletWithItsSecret(): Unit = runBlocking {
        val coordinator = mockk<WalletSecretMutationCoordinator>()
        val prototype = slot<MetaAccountLocal>()
        val secret = slot<String>()
        coEvery { coordinator.create(capture(prototype), null, null, capture(secret)) } returns 11L

        val id = TonAccountRepository(coordinator).create(AddAccountPayload.Ton("Toncoin", words, isBackedUp = true))

        assertEquals(11L, id)
        with(prototype.captured) {
            assertEquals("Toncoin", name)
            assertTrue(isSelected)
            assertTrue(isBackedUp)
            assertFalse(initialized)
            assertNull(substratePublicKey)
            assertNull(ethereumPublicKey)
            assertEquals(32, tonPublicKey!!.size)
        }
        assertTrue(secret.captured.isNotBlank())

        val again = slot<MetaAccountLocal>()
        coEvery { coordinator.create(capture(again), null, null, any()) } returns 12L
        TonAccountRepository(coordinator).create(AddAccountPayload.Ton("Copy", words, isBackedUp = false))
        assertArrayEquals(prototype.captured.tonPublicKey, again.captured.tonPublicKey)
        coVerify(exactly = 2) { coordinator.create(any(), null, null, any()) }
    }

    @Test
    fun tonIdentityConflictsBecomeAlreadyExistsAndOtherFailuresPassThrough(): Unit = runBlocking {
        val coordinator = mockk<WalletSecretMutationCoordinator>()
        val repository = TonAccountRepository(coordinator)
        val payload = AddAccountPayload.Ton("TON", words, false)

        coEvery { coordinator.create(any(), null, null, any()) } throws
            WalletSecretMutationCoordinatorException(WalletMutationFailureReason.IDENTITY_CONFLICT, "exists")
        try {
            repository.create(payload)
            fail("an identity conflict must be reported as an existing account")
        } catch (expected: AccountAlreadyExistsException) {
            assertNotEquals(null, expected)
        }

        val other = WalletSecretMutationCoordinatorException(WalletMutationFailureReason.STATE_CONFLICT, "busy")
        coEvery { coordinator.create(any(), null, null, any()) } throws other
        try {
            repository.create(payload)
            fail("other coordinator failures must not be hidden")
        } catch (thrown: WalletSecretMutationCoordinatorException) {
            assertSame(other, thrown)
        }
    }
}
