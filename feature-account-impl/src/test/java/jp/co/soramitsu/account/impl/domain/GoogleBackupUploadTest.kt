package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Critique T-7: an unverified phrase or seed never reaches Drive, while JSON stays available. */
class GoogleBackupUploadTest {

    private val account = DecryptedBackupAccount(
        name = "Wallet",
        address = "5FM25N8HGtacre9TnWQGRK32d1kUp1Gs6Dd7YiYpSPZLjmj6",
        mnemonicPhrase = "synthetic phrase",
        cryptoType = CryptoType.ED25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED, BackupAccountType.JSON),
        seed = Seed(substrateSeed = "0x01", ethSeed = "0x02"),
        json = Json(substrateJson = "{}", ethJson = null)
    )

    @Test
    fun `a verified backup is uploaded once with every type`() = runBlocking<Unit> {
        val uploads = mutableListOf<List<BackupAccountType>>()

        val leftOut = uploadVerifiedGoogleBackup(account) { uploads += it.backupAccountType }

        assertEquals(emptySet<BackupAccountType>(), leftOut)
        assertEquals(listOf(account.backupAccountType), uploads)
    }

    @Test
    fun `a refused phrase is left out and the seed and json uploaded`() = runBlocking<Unit> {
        val uploaded = mutableListOf<DecryptedBackupAccount>()

        val leftOut = uploadVerifiedGoogleBackup(account, refusing(setOf(BackupAccountType.PASSPHRASE), uploaded))

        assertEquals(setOf(BackupAccountType.PASSPHRASE), leftOut)
        assertEquals(listOf(listOf(BackupAccountType.SEED, BackupAccountType.JSON)), uploaded.map { it.backupAccountType })
    }

    @Test
    fun `an unverified phrase and seed leave a json backup`() = runBlocking<Unit> {
        val uploaded = mutableListOf<DecryptedBackupAccount>()

        val leftOut = uploadVerifiedGoogleBackup(account, refusing(setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), uploaded))

        assertEquals(setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED), leftOut)
        assertEquals(listOf(listOf(BackupAccountType.JSON)), uploaded.map { it.backupAccountType })
    }

    @Test
    fun `nothing is uploaded when no remaining type restores the wallet`() = runBlocking<Unit> {
        val phraseOnly = account.copy(backupAccountType = listOf(BackupAccountType.PASSPHRASE))
        val uploaded = mutableListOf<DecryptedBackupAccount>()

        val failure = runCatching {
            uploadVerifiedGoogleBackup(phraseOnly, refusing(setOf(BackupAccountType.PASSPHRASE), uploaded))
        }.exceptionOrNull() as BackupSecretNotVerifiedException

        assertEquals(BackupAccountType.PASSPHRASE, failure.backupAccountType)
        assertEquals(emptyList<DecryptedBackupAccount>(), uploaded)
    }

    @Test
    fun `a refused json is the final answer`() = runBlocking<Unit> {
        val uploaded = mutableListOf<DecryptedBackupAccount>()

        val failure = runCatching {
            uploadVerifiedGoogleBackup(account, refusing(BackupAccountType.entries.toSet(), uploaded))
        }.exceptionOrNull() as BackupSecretNotVerifiedException

        assertEquals(BackupAccountType.JSON, failure.backupAccountType)
        assertEquals(emptyList<DecryptedBackupAccount>(), uploaded)
    }

    @Test
    fun `a refusal of an undeclared type is passed on instead of retried forever`() = runBlocking<Unit> {
        val refusal = BackupSecretNotVerifiedException(BackupAccountType.SEED)
        var attempts = 0

        val failure = runCatching {
            uploadVerifiedGoogleBackup(account.copy(backupAccountType = listOf(BackupAccountType.JSON))) {
                attempts++
                throw refusal
            }
        }.exceptionOrNull()

        assertSame(refusal, failure)
        assertEquals(1, attempts)
    }

    @Test
    fun `other upload failures are not retried`() = runBlocking<Unit> {
        val offline = IllegalStateException("offline")
        var attempts = 0

        val failure = runCatching {
            uploadVerifiedGoogleBackup(account) {
                attempts++
                throw offline
            }
        }.exceptionOrNull()

        assertSame(offline, failure)
        assertEquals(1, attempts)
    }

    // Mirrors BackupService: the first declared type that does not restore the wallet is refused.
    private fun refusing(
        unverified: Set<BackupAccountType>,
        uploaded: MutableList<DecryptedBackupAccount>
    ): suspend (DecryptedBackupAccount) -> Unit = { candidate ->
        candidate.backupAccountType.firstOrNull { it in unverified }?.let { throw BackupSecretNotVerifiedException(it) }
        uploaded += candidate
    }
}
