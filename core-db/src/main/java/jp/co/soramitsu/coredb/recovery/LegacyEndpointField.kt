package jp.co.soramitsu.coredb.recovery

/** Only these original stored fields may be inspected; values are never interpreted as URLs. */
enum class LegacyEndpointField(internal val column: String) {
    NAME("name"),
    ENDPOINT("link")
}
