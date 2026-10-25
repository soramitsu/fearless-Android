package jp.co.soramitsu.account.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.westendChainId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountInteractorGoogleBackupTest {

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val backupService = mockk<BackupService>()
    private val interactor = AccountInteractorImpl(
        accountRepository = accountRepository,
        fileProvider = mockk(),
        preferences = mockk(relaxed = true),
        backupService = backupService,
        walletInteractor = mockk()
    )

    private val accountId = ByteArray(32) { 1 }
    private val ownAddress = accountId.toAddress(WESTEND_PREFIX)
    private val fundedAddress = ByteArray(32) { 2 }.toAddress(WESTEND_PREFIX)
    private val phoneCopy = BackupAccountMeta("Wallet", ownAddress, "phone")
    private val tabletCopy = BackupAccountMeta("Wallet", ownAddress, "tablet")

    // Another account's backup, the one a drifted phrase was imported from.
    private val fundedCopy = BackupAccountMeta("Funded", fundedAddress, "funded")

    @Before
    fun setup() {
        val westend = mockk<Chain> {
            every { id } returns westendChainId
            every { externalApi } returns null
            every { ecosystem } returns Ecosystem.Substrate
            every { addressPrefix } returns WESTEND_PREFIX.toInt()
        }
        coEvery { accountRepository.getChain(westendChainId) } returns westend
        coEvery { accountRepository.getMetaAccount(3L) } returns wallet(googleBackupAddress = null)
        coEvery { backupService.getBackupCopies() } returns listOf(tabletCopy, phoneCopy, fundedCopy)
    }

    @Test
    fun `the wallet's copies are the ones under its own address`() = runBlocking<Unit> {
        coEvery { accountRepository.getMetaAccount(3L) } returns wallet(googleBackupAddress = fundedAddress)

        assertEquals(listOf(tabletCopy, phoneCopy), interactor.getGoogleBackupCopies(walletId = 3L))
    }

    @Test
    fun `a deleted backup clears the wallet's drive backup mark`() = runBlocking<Unit> {
        coEvery { backupService.deleteBackupAccount(ownAddress, any()) } returns Unit

        interactor.deleteGoogleBackupAccount(walletId = 3L, copies = listOf(tabletCopy, phoneCopy))

        coVerify(exactly = 1) { backupService.deleteBackupAccount(ownAddress, listOf("tablet", "phone")) }
        coVerify(exactly = 1) { accountRepository.updateWalletOnGoogleBackupDelete(3L) }
    }

    @Test
    fun `delete never reaches the backup the wallet was imported from`() = runBlocking<Unit> {
        // The wallet records the funded account's backup address, but its own keys are another account.
        coEvery { accountRepository.getMetaAccount(3L) } returns wallet(googleBackupAddress = fundedAddress)
        coEvery { backupService.deleteBackupAccount(ownAddress, any()) } returns Unit

        interactor.deleteGoogleBackupAccount(walletId = 3L, copies = listOf(phoneCopy, fundedCopy))

        coVerify(exactly = 1) { backupService.deleteBackupAccount(ownAddress, listOf("phone")) }
        coVerify(exactly = 0) { backupService.deleteBackupAccount(fundedAddress, any()) }
    }

    @Test
    fun `a backup already gone from drive counts as deleted`() = runBlocking<Unit> {
        coEvery { backupService.deleteBackupAccount(ownAddress, any()) } throws FileNotFoundException()

        interactor.deleteGoogleBackupAccount(walletId = 3L, copies = listOf(phoneCopy))

        coVerify(exactly = 1) { accountRepository.updateWalletOnGoogleBackupDelete(3L) }
    }

    @Test
    fun `a failed delete is reported and keeps the mark`() = runBlocking<Unit> {
        // Previously swallowed: the screen showed nothing and the backup stayed on Drive.
        val offline = IOException("offline")
        coEvery { backupService.deleteBackupAccount(ownAddress, any()) } throws offline

        val failure = runCatching { interactor.deleteGoogleBackupAccount(walletId = 3L, copies = listOf(phoneCopy)) }.exceptionOrNull()

        assertSame(offline, failure)
        coVerify(exactly = 0) { accountRepository.updateWalletOnGoogleBackupDelete(any()) }
    }

    @Test
    fun `google backup availability follows the backup service`() {
        every { backupService.isAvailable } returns false
        assertFalse(interactor.isGoogleBackupAvailable())

        every { backupService.isAvailable } returns true
        assertTrue(interactor.isGoogleBackupAvailable())
    }

    private fun wallet(googleBackupAddress: String?) = MetaAccount(
        id = 3L,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = accountId,
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = accountId,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = googleBackupAddress,
        name = "Wallet",
        initialized = true
    )

    private companion object {
        const val WESTEND_PREFIX: Short = 42
    }
}
