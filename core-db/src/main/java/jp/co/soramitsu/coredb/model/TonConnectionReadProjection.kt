package jp.co.soramitsu.coredb.model

/**
 * A CursorWindow-safe view of a [TonConnectionLocal].
 *
 * DAO queries return a column only when SQLite has proved its storage type and
 * character/byte bounds. Otherwise they return a small sentinel and set
 * [rowWithinBounds] to false, allowing the repository to fail closed without
 * ever materializing attacker-sized text in the app process.
 */
data class TonConnectionReadProjection(
    val metaId: Long,
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String,
    val source: String,
    val clientIdUtf8Bytes: Long,
    val nameUtf8Bytes: Long,
    val iconUtf8Bytes: Long,
    val urlUtf8Bytes: Long,
    val sourceUtf8Bytes: Long,
    val rowWithinBounds: Boolean
)
