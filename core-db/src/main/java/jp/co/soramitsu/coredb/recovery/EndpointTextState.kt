package jp.co.soramitsu.coredb.recovery

/** Unreadable and deferred text remain distinct from an empty valid value. */
enum class EndpointTextState { AVAILABLE, OVERSIZED, INVALID_UTF8 }
