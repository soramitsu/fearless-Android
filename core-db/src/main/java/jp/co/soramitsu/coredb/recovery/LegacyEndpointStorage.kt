package jp.co.soramitsu.coredb.recovery

/** Original SQLite storage, without interpreting an archived value as a network or URL. */
enum class LegacyEndpointStorage { NULL, INTEGER, REAL, TEXT, BLOB }
