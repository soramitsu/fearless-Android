package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/**
 * Supplies only chain discovery data fetched successfully during this app
 * process. Persisted database state is deliberately excluded so a failed or
 * not-yet-run sync cannot resurrect an XCM route removed by current discovery.
 */
interface XcmDiscoverySnapshotProvider {
    suspend fun getCurrentProcessXcmDiscoveryChains(): List<Chain>
}
