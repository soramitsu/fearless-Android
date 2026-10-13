package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class LegacyGoogleBackupAccountImporterTest {

    @Test
    fun `mnemonic backup restores its independent EVM key in one creation`() = runTest {
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccountFromBackup(any(), any())).thenReturn(Result.success(77L))
        val backedUpEvmKey = "0x" + "00".repeat(31) + "07"
        val backup = backup(seed = Seed(substrateSeed = null, ethSeed = backedUpEvmKey))

        importLegacyGoogleBackupAccount(interactor, backup, "password")

        val payload = argumentCaptor<AddAccountPayload.SubstrateOrEvm>()
        verify(interactor).createAccountFromBackup(payload.capture(), eq(backedUpEvmKey))
        assertEquals(backup.mnemonicPhrase, payload.firstValue.mnemonic)
        assertEquals("", payload.firstValue.ethereumDerivationPath)
        verify(interactor, never()).createAccount(any())
        verify(interactor, never()).importFromSeed(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `mnemonic backup without EVM seed keeps existing derivation`() = runTest {
        val interactor = mock<AccountInteractor>()
        whenever(interactor.createAccount(any())).thenReturn(Result.success(78L))

        importLegacyGoogleBackupAccount(interactor, backup(seed = null), "password")

        verify(interactor).createAccount(any())
        verify(interactor, never()).createAccountFromBackup(any(), any())
    }

    @Test
    fun `wallet creation failure cannot be reported as restored`() = runTest {
        val interactor = mock<AccountInteractor>()
        val failure = IllegalStateException("synthetic mutation failure")
        whenever(interactor.createAccountFromBackup(any(), any())).thenReturn(Result.failure(failure))
        val backup = backup(seed = Seed(substrateSeed = null, ethSeed = "synthetic-key"))

        val actual = runCatching {
            importLegacyGoogleBackupAccount(interactor, backup, "password")
        }.exceptionOrNull()

        assertSame(failure, actual)
    }

    @Test
    fun `backup without recoverable material fails closed`() = runTest {
        val interactor = mock<AccountInteractor>()
        val backup = backup(mnemonicPhrase = null, seed = null)

        val actual = runCatching {
            importLegacyGoogleBackupAccount(interactor, backup, "password")
        }.exceptionOrNull()

        assertEquals("Backup has no recoverable wallet material", actual?.message)
        verifyNoInteractions(interactor)
    }

    private fun backup(
        mnemonicPhrase: String? = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        seed: Seed?
    ) = DecryptedBackupAccount(
        name = "Recovered wallet",
        address = "backup-address",
        mnemonicPhrase = mnemonicPhrase,
        substrateDerivationPath = "",
        ethDerivationPath = null,
        cryptoType = CryptoType.ED25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE),
        seed = seed,
        json = null
    )
}
