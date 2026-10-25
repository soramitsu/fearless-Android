package jp.co.soramitsu.backup

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.backup.data.InMemoryBackupFileIndex
import jp.co.soramitsu.backup.domain.AccountJsonMapper
import jp.co.soramitsu.backup.domain.BackupAccountCrypto
import jp.co.soramitsu.backup.domain.BackupSecretVerifier
import jp.co.soramitsu.backup.domain.Storage
import jp.co.soramitsu.backup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.FileMeta
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.core.models.CryptoType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json as KotlinxJson

/**
 * Backup files written by the production shared_features code (1.1.1.44 as shipped in 4.2.0, and
 * 1.0.1 as shipped in 2.3.1), with synthetic test phrases. See legacy-backup/manifest.json.
 */
internal object LegacyBackupFixtures {

    class Fixture(
        val file: String,
        val password: String,
        val content: String,
        val expected: DecryptedBackupAccount,
        val ethereumAddress: String?
    )

    val all: List<Fixture> by lazy {
        KotlinxJson.parseToJsonElement(resource("manifest.json")).jsonObject.getValue("fixtures").jsonArray.map {
            val entry = it.jsonObject
            val file = entry.string("file")!!
            Fixture(
                file = file,
                password = entry.string("password")!!,
                content = resource(file),
                expected = entry.getValue("expected").jsonObject.toDecryptedBackupAccount(),
                ethereumAddress = entry["identity"]?.jsonObject?.string("ethereumAddress")
            )
        }
    }

    fun named(file: String): Fixture = all.single { it.file == file }

    private fun resource(name: String): String {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("legacy-backup/$name")) {
            "Missing fixture $name"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

    private fun JsonObject.toDecryptedBackupAccount() = DecryptedBackupAccount(
        name = string("name")!!,
        address = string("address")!!,
        mnemonicPhrase = string("mnemonicPhrase"),
        substrateDerivationPath = string("substrateDerivationPath"),
        ethDerivationPath = string("ethDerivationPath"),
        cryptoType = CryptoType.valueOf(string("cryptoType")!!),
        backupAccountType = getValue("backupAccountType").jsonArray.map { BackupAccountType.valueOf(it.jsonPrimitive.content) },
        seed = (get("seed") as? JsonObject)?.let { Seed(it.string("substrateSeed"), it.string("ethSeed")) },
        json = (get("json") as? JsonObject)?.let { Json(it.string("substrateJson"), it.string("ethJson")) }
    )
}

/** In-memory Drive: [appFiles] live in the backup folder, [webFiles] in the app data root. */
internal class FakeStorage : Storage {
    val appFiles = mutableListOf<FileMeta>()
    val webFiles = mutableListOf<FileMeta>()
    val contents = mutableMapOf<String, String>()
    val missingFileIds = mutableSetOf<String>()
    val deletedFileIds = mutableListOf<String>()
    val savedFileIds = mutableListOf<String>()
    var appListCalls = 0
    var webListCalls = 0
    var signInCalls = 0
    var logoutCalls = 0
    var touched = false

    fun addAppFile(
        address: String,
        fileId: String,
        content: String,
        name: String = "wallet",
        modifiedTime: Long? = null
    ) {
        appFiles += FileMeta(fileName = "$address.json", description = name, fileId = fileId, modifiedTime = modifiedTime)
        contents[fileId] = content
    }

    fun addWebFile(
        fileName: String,
        description: String,
        fileId: String,
        content: String
    ) {
        webFiles += FileMeta(fileName = fileName, description = description, fileId = fileId)
        contents[fileId] = content
    }

    override suspend fun signIn(activityResultLauncher: ActivityResultLauncher<Intent>): Boolean {
        touched = true
        signInCalls++
        return true
    }

    override suspend fun saveFile(
        name: String,
        description: String,
        content: String
    ): String {
        touched = true
        val fileId = "saved-${savedFileIds.size}"
        savedFileIds += fileId
        appFiles += FileMeta(fileName = name, description = description, fileId = fileId, modifiedTime = Long.MAX_VALUE)
        contents[fileId] = content
        return fileId
    }

    override suspend fun getFileContent(fileId: String): String {
        touched = true
        if (fileId in missingFileIds) throw FileNotFoundException()
        return contents.getValue(fileId)
    }

    override suspend fun getListOfFileHeads(): List<FileMeta> {
        touched = true
        appListCalls++
        return appFiles.toList()
    }

    override suspend fun getWebListOfFileHeads(): List<FileMeta> {
        touched = true
        webListCalls++
        return webFiles.toList()
    }

    override suspend fun deleteFile(fileId: String) {
        touched = true
        if (fileId in missingFileIds) throw FileNotFoundException()
        deletedFileIds += fileId
        appFiles.removeAll { it.fileId == fileId }
    }

    override suspend fun logout() {
        logoutCalls++
    }
}

internal fun backupService(
    storage: Storage,
    secretVerifier: BackupSecretVerifier = BackupSecretVerifier(),
    isAvailable: Boolean = true
) = BackupService(
    storage = storage,
    accountJsonMapper = AccountJsonMapper(),
    backupAccountCrypto = BackupAccountCrypto(),
    secretVerifier = secretVerifier,
    fileIndex = InMemoryBackupFileIndex(),
    isAvailable = isAvailable
)
