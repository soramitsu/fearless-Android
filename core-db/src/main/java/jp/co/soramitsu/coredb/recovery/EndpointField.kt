package jp.co.soramitsu.coredb.recovery

/** Fixed original fields. Callers cannot supply SQL identifiers. */
enum class EndpointField { NAME, ENDPOINT, CHAIN_REFERENCE, NETWORK_TYPE, IS_DEFAULT, IS_ACTIVE }
