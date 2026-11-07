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
    var failure: Throwable? = null
) : ReownLegacySource {
    val handedOutPassphrases = mutableListOf<ByteArray>()
    var keyChainReads = 0

    override fun hasLegacyData(): Boolean = hasData

    override fun readKeyChain(): Map<String, String> {
        keyChainReads += 1
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

/** Attempts in memory. */
internal class MemoryImportAttempts(var value: Int = 0) : ReownImportAttempts {
    override fun count(): Int = value

    override fun record() {
        value += 1
    }

    override fun refund() {
        value = (value - 1).coerceAtLeast(0)
    }
}
