package jp.co.soramitsu.backup

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.data.GoogleDriveStorage
import jp.co.soramitsu.backup.data.InMemoryBackupFileIndex
import jp.co.soramitsu.backup.data.WebBackupEntry
import jp.co.soramitsu.backup.domain.AccountJsonMapper
import jp.co.soramitsu.backup.domain.BackupAccountCrypto
import jp.co.soramitsu.backup.domain.BackupSecretVerifier
import jp.co.soramitsu.backup.domain.Storage
import jp.co.soramitsu.backup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.backup.domain.exceptions.BackupSecretNotVerifiedException
import jp.co.soramitsu.backup.domain.exceptions.BackupUnavailableException
import jp.co.soramitsu.backup.domain.exceptions.BackupWrongPasswordException
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.exceptions.InsufficientFilePermissionsException
import jp.co.soramitsu.backup.domain.exceptions.StorageQuotaExceededException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.FileMeta
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Google Drive wallet backup, ported from shared_features 1.1.1.44 (the version app 4.2.0 shipped)
 * so existing backups keep restoring and new ones stay readable by older apps.
 */
class BackupService internal constructor(
    private val storage: Storage,
    private val accountJsonMapper: AccountJsonMapper,
    private val backupAccountCrypto: BackupAccountCrypto,
    private val secretVerifier: BackupSecretVerifier,
    private val fileIndex: InMemoryBackupFileIndex,
    /** False when the build has no Google OAuth web client, so Drive sign-in cannot succeed. */
    val isAvailable: Boolean
) {

    suspend fun authorize(launcher: ActivityResultLauncher<Intent>): Boolean {
        if (!isAvailable) return false

        return storage.signIn(launcher)
    }

    /**
     * Uploads a new backup file. Refuses, without uploading anything, a phrase or seed that does not
     * restore the wallet filed under [DecryptedBackupAccount.address].
     */
    @Throws(
        BackupSecretNotVerifiedException::class,
        UnauthorizedException::class,
        AuthConsentException::class,
        StorageQuotaExceededException::class,
        InsufficientFilePermissionsException::class
    )
    suspend fun saveBackupAccount(account: DecryptedBackupAccount, password: String) {
        requireAvailable()
        val verifiedAccount = secretVerifier.verifiedForUpload(account)
        val encryptedBackupAccount = backupAccountCrypto.encryptAccount(verifiedAccount, password)

        val fileId = storage.saveFile(
            name = "${account.address}.json",
            description = account.name,
            content = accountJsonMapper.mapEncryptedAccountToJson(encryptedBackupAccount)
        )

        fileIndex.addAppFile(
            account.address,
            FileMeta(fileName = "${account.address}.json", description = account.name, fileId = fileId)
        )
    }

    /** App backups, one entry per address even when several devices uploaded the same wallet. */
    @Throws(UnauthorizedException::class, AuthConsentException::class)
    suspend fun getBackupAccounts(): List<BackupAccountMeta> {
        requireAvailable()

        return fileIndex.replaceAppFiles(storage.getListOfFileHeads()).map { (address, files) ->
            val newest = files.first()
            BackupAccountMeta(
                name = newest.description,
                address = address,
                fileId = newest.fileId
            )
        }
    }

    /**
     * Every app backup, newest first for each address. Each device that backs up a wallet adds its own
     * copy under the same address, possibly with another password or other recovery material.
     */
    @Throws(UnauthorizedException::class, AuthConsentException::class)
    suspend fun getBackupCopies(): List<BackupAccountMeta> {
        requireAvailable()

        return fileIndex.replaceAppFiles(storage.getListOfFileHeads()).flatMap { (address, files) ->
            files.map { file ->
                BackupAccountMeta(
                    name = file.description,
                    address = address,
                    fileId = file.fileId
                )
            }
        }
    }

    /** Tries the backups filed under [address] newest first and returns the first [password] opens. */
    @Throws(
        UnauthorizedException::class,
        AuthConsentException::class,
        DecodingException::class,
        DecryptionException::class,
        FileNotFoundException::class
    )
    suspend fun importBackupAccount(address: String, password: String): DecryptedBackupAccount {
        requireAvailable()
        val candidates = appFiles(address)
        if (candidates.isEmpty()) throw FileNotFoundException()

        val failures = mutableListOf<Exception>()
        for (file in candidates) {
            try {
                return decryptAppBackup(storage.getFileContent(file.fileId), password)
            } catch (e: DecryptionException) {
                failures += e
            } catch (e: DecodingException) {
                failures += e
            } catch (e: FileNotFoundException) {
                failures += e
            }
        }
        // A wrong password is the failure the user can fix, so it wins over damaged or missing copies.
        throw failures.firstOrNull { it is DecryptionException } ?: failures.first()
    }

    /**
     * Chrome extension backups. Each is a polkadot-js JSON named "<wallet name>.json" whose
     * description is "<substrate address>[/<ethereum JSON file id>]"; the Ethereum JSON is listed
     * on its own with its address as description and is folded into the entry that references it.
     */
    @Throws(UnauthorizedException::class, AuthConsentException::class)
    suspend fun getWebBackupAccounts(): List<BackupAccountMeta> {
        requireAvailable()
        // Every extension version names its files "<name>.json". Other files in the app data root, such
        // as the passkey backups ("fearless-passkey-*.bin"), are not wallets and must not be offered.
        val webListOfFileHeads = storage.getWebListOfFileHeads().filter { it.fileName.endsWith(".json") }
        val ethereumFileIds = webListOfFileHeads.mapNotNull(::getWebEthereumFileId).toSet()

        val entries = webListOfFileHeads.filter { it.fileId !in ethereumFileIds }.map(::createWebBackupEntry)
        fileIndex.replaceWebEntries(entries)

        return entries.map { it.meta }
    }

    private fun getWebEthereumFileId(file: FileMeta): String? =
        // web description format: <substrate google backup address>[/<ethereum json backup fileId>]
        file.description.split("/").getOrNull(1)?.takeIf { it.isNotEmpty() }

    private fun createWebBackupEntry(file: FileMeta): WebBackupEntry {
        return WebBackupEntry(
            meta = BackupAccountMeta(
                name = file.fileName.substringBefore(".json"),
                address = file.description.substringBefore("/"),
                fileId = file.fileId
            ),
            substrateFileId = file.fileId,
            ethereumFileId = getWebEthereumFileId(file)
        )
    }

    @Throws(UnauthorizedException::class, AuthConsentException::class, FileNotFoundException::class)
    suspend fun importWebBackupAccount(meta: BackupAccountMeta): DecryptedBackupAccount {
        requireAvailable()
        val fileId = meta.fileId ?: return importWebBackupAccount(meta.address, meta.name)
        val entry = fileIndex.webEntry(fileId)
            ?: getWebBackupAccounts().let { fileIndex.webEntry(fileId) }
            ?: throw FileNotFoundException()

        return importWebBackupEntry(entry)
    }

    /**
     * Resolves the extension backup by address and name. Substrate-only extension backups all have an
     * empty address, so prefer the overload taking the listed [BackupAccountMeta].
     */
    @Throws(UnauthorizedException::class, AuthConsentException::class, FileNotFoundException::class)
    suspend fun importWebBackupAccount(address: String, name: String): DecryptedBackupAccount {
        requireAvailable()
        val candidates = fileIndex.webEntries(address).ifEmpty {
            getWebBackupAccounts()
            fileIndex.webEntries(address)
        }
        val entry = candidates.firstOrNull { it.meta.name == name } ?: candidates.firstOrNull() ?: throw FileNotFoundException()

        return importWebBackupEntry(entry).copy(name = name)
    }

    private suspend fun importWebBackupEntry(entry: WebBackupEntry): DecryptedBackupAccount {
        val substrateJson = storage.getFileContent(entry.substrateFileId)
        val ethereumJson = entry.ethereumFileId?.let { storage.getFileContent(it) }

        // An Ethereum JSON nobody references is left over from an export whose Substrate upload failed.
        val json = if (ethereumJson == null && substrateJson.isEthereumKeyringJson()) {
            Json(substrateJson = null, ethJson = substrateJson)
        } else {
            Json(substrateJson, ethereumJson)
        }

        return DecryptedBackupAccount(
            name = entry.meta.name,
            address = entry.meta.address,
            cryptoType = CryptoType.ED25519, // any value, cryptoType not used when importing json
            backupAccountType = listOf(BackupAccountType.JSON),
            json = json
        )
    }

    /**
     * Which of an imported backup's phrase and seed restore the wallet it is filed under; null when its
     * address cannot be checked. Apps before this version uploaded both unchecked, so an importer
     * should skip one that restores another account and use the seed or JSON beside it.
     */
    suspend fun restoringSecretTypes(account: DecryptedBackupAccount): Set<BackupAccountType>? {
        // Key derivation, kept off the caller's (often main) thread.
        return withContext(Dispatchers.Default) {
            secretVerifier.restoringSecretTypes(account)
        }
    }

    /**
     * Deletes the app backups [fileIds] filed under [address]: the copies the user confirmed. Drive
     * deletes skip the trash, so files under another address, copies uploaded since and extension
     * backups are never touched.
     */
    @Throws(UnauthorizedException::class, AuthConsentException::class, FileNotFoundException::class)
    suspend fun deleteBackupAccount(address: String, fileIds: Collection<String>) {
        requireAvailable()
        val candidates = appFiles(address).filter { it.fileId in fileIds }
        if (candidates.isEmpty()) throw FileNotFoundException()

        var notFound: FileNotFoundException? = null
        var deleted = 0
        for (file in candidates) {
            try {
                storage.deleteFile(file.fileId)
                deleted++
            } catch (e: FileNotFoundException) {
                notFound = e
            }
            fileIndex.removeAppFile(address, file.fileId)
        }
        // As before, a backup that was already gone is reported to the caller.
        if (deleted == 0 && notFound != null) throw notFound
    }

    suspend fun logout() {
        fileIndex.clear()
        storage.logout()
    }

    private suspend fun appFiles(address: String): List<FileMeta> {
        return fileIndex.appFiles(address).ifEmpty {
            getBackupAccounts()
            fileIndex.appFiles(address)
        }
    }

    private fun decryptAppBackup(content: String, password: String): DecryptedBackupAccount {
        val encryptedBackupAccount = accountJsonMapper.mapJsonStringToEncryptedAccount(content)
        return try {
            backupAccountCrypto.decryptAccount(encryptedBackupAccount, password)
        } catch (e: IllegalArgumentException) {
            // The mapper rejected malformed files, so this is a SEED backup whose seeds did not open.
            throw BackupWrongPasswordException(e)
        }
    }

    private fun requireAvailable() {
        if (!isAvailable) throw BackupUnavailableException()
    }

    companion object {
        fun create(context: Context, token: String): BackupService {
            val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

            return BackupService(
                storage = GoogleDriveStorage(context, token),
                accountJsonMapper = AccountJsonMapper(),
                backupAccountCrypto = BackupAccountCrypto(),
                secretVerifier = BackupSecretVerifier(),
                fileIndex = InMemoryBackupFileIndex(),
                isAvailable = token.isNotBlank() || isDebuggable
            )
        }
    }
}

// Anything that is not a polkadot-js keystore is left to the JSON importer to reject.
private fun String.isEthereumKeyringJson(): Boolean {
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(this)
            .jsonObject["encoding"]
            ?.jsonObject
            ?.get("content")
            ?.jsonArray
            ?.any { it.jsonPrimitive.content == "ethereum" } == true
    }.getOrDefault(false)
}
