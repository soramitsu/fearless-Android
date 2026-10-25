package jp.co.soramitsu.coredb.recovery

/** Stored capture metadata; original user_version can differ from the capture edge. */
data class EndpointSourceProvenance(val sourceEdgeVersion: Int, val originalUserVersion: Int) {
    init { require(sourceEdgeVersion >= 0 && originalUserVersion >= 0) }
}
