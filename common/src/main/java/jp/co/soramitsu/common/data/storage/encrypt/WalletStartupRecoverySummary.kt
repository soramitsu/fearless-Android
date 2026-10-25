package jp.co.soramitsu.common.data.storage.encrypt

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the start-up gate left for the user to look at. The app opens to the
 * wallet list whatever this says (addendum U0.1); the wallet screens use it
 * to point at the recovery items instead of stopping the app.
 *
 * Counts are null when the inventory could not be read on this start.
 */
data class WalletStartupRecoverySummary(
    val itemCount: Int?,
    val archivedCount: Int?
) {
    val needsAttention: Boolean
        get() = itemCount == null || itemCount > 0
}

@Singleton
class WalletStartupRecoverySummaryHolder @Inject constructor() {

    private val mutableSummary = MutableStateFlow<WalletStartupRecoverySummary?>(null)

    /** Null until the gate has run in this process. */
    val summary: StateFlow<WalletStartupRecoverySummary?> = mutableSummary.asStateFlow()

    fun publish(summary: WalletStartupRecoverySummary) {
        mutableSummary.value = summary
    }
}
