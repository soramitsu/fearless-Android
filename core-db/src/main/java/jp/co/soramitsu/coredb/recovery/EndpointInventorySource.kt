package jp.co.soramitsu.coredb.recovery

/** Fixed independent stores; a source identity never grants attachment or activation. */
enum class EndpointInventorySource { LEGACY, HISTORICAL, UPGRADE_ENTRY, CURRENT, OWNED }
