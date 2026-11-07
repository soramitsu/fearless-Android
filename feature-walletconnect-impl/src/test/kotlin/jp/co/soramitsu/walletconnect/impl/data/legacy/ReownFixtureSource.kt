package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.bytes
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.passphrase
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section

/** What the SDK left, as the fixtures hold it: real SQLCipher databases and the key chain they go with. */
internal class ReownFixtureSource(
    var hasData: Boolean = true,
    var keyChain: Map<String, String> = fixtureKeyChain(),
    var passphrase: () -> ByteArray? = { passphrase("reown") },
    var databases: Map<String, LegacyDatabaseFiles> = mapOf(
        ReownLegacySnapshotReader.SIGN_DATABASE to LegacyDatabaseFiles(bytes("reown-sign.db")),
        ReownLegacySnapshotReader.CORE_DATABASE to LegacyDatabaseFiles(bytes("reown-core.db"))
    ),
    var failure: Exception? = null
) : ReownLegacySource {
    val handedOutPassphrases = mutableListOf<ByteArray>()

    override fun hasLegacyData(): Boolean = hasData

    override fun readKeyChain(): Map<String, String> {
        failure?.let { throw it }
        return keyChain
    }

    override fun readDatabasePassphrase(): ByteArray? = passphrase()?.also { handedOutPassphrases += it }

    override fun readDatabase(name: String): LegacyDatabaseFiles? = databases[name]

    companion object {
        fun fixtureKeyChain(): Map<String, String> =
            section("reown").getAsJsonObject("keychain").entrySet().associate { it.key to it.value.asString }
    }
}

/** A ledger in memory. */
internal class MemoryImportLedger(
    override var isDone: Boolean = false,
    override var keepsLegacyFiles: Boolean = false,
    override var failedAttempts: Int = 0
) : ReownImportLedger {
    override fun markDone(keepLegacyFiles: Boolean) {
        isDone = true
        keepsLegacyFiles = keepLegacyFiles
    }

    override fun recordFailedAttempt() {
        failedAttempts += 1
    }
}
