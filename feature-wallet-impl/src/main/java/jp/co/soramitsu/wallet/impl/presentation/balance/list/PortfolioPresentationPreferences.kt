package jp.co.soramitsu.wallet.impl.presentation.balance.list

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.google.gson.JsonParser
import com.google.gson.JsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-wallet portfolio view preferences. They are conveniences: a failed read or write falls back
 * to the default, and nothing here holds key material or a per-asset Shown/Hidden preference.
 */
@Singleton
class PortfolioPresentationPreferences @Inject constructor(
    private val preferences: Preferences
) {

    /** Defaults to By token on a new install and for an invalid saved value. */
    fun observeLens(metaId: Long): Flow<PortfolioLens> = observeString(viewModeKey(metaId)).map { raw ->
        PortfolioLens.entries.firstOrNull { it.name == raw } ?: PortfolioLens.ByToken
    }.distinctUntilChanged()

    fun setLens(metaId: Long, lens: PortfolioLens) { runCatching { preferences.putString(viewModeKey(metaId), lens.name) } }

    fun observeUi(metaId: Long): Flow<PortfolioUiState> = combine(
        observeLens(metaId), observeSet(expandedGroupsKey(metaId)), observeSet(collapsedNetworksKey(metaId)),
        observeSet(expandedZeroGroupsKey(metaId)), observeString(zeroSummaryKey(metaId)), observeString(VALUE_NOTICE_KEY)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PortfolioUiState(values[0] as PortfolioLens, values[1] as Set<String>, values[2] as Set<String>,
            values[3] as Set<String>, values[4] == "true", values[5] == "true")
    }

    fun toggleGroup(metaId: Long, id: String) = toggle(expandedGroupsKey(metaId), id)
    fun toggleNetwork(metaId: Long, id: String) = toggle(collapsedNetworksKey(metaId), id)
    fun toggleZeroGroup(metaId: Long, id: String) = toggle(expandedZeroGroupsKey(metaId), id)
    fun toggleManageSection(metaId: Long, id: String) = toggle(manageSectionsKey(metaId), id)
    fun observeManageSections(metaId: Long): Flow<Set<String>> = observeSet(manageSectionsKey(metaId))
    fun setZeroSummaryExpanded(metaId: Long, expanded: Boolean) {
        runCatching { preferences.putString(zeroSummaryKey(metaId), expanded.toString()) }
    }
    fun acknowledgeValueNotice() { runCatching { preferences.putString(VALUE_NOTICE_KEY, "true") } }

    private fun observeString(key: String): Flow<String?> =
        runCatching { preferences.stringFlow(key) }.getOrElse { flowOf(null) }.catch { emit(null) }

    private fun observeSet(key: String): Flow<Set<String>> = observeString(key).map(::decodeSet).distinctUntilChanged()

    private fun toggle(key: String, id: String) {
        if (id.isBlank() || id.length > MAX_PRESENTATION_ID_LENGTH) return
        runCatching {
            val current = decodeSet(preferences.getString(key))
            val changed = if (id in current) current - id else (current + id).takeLastBounded()
            val json = JsonArray().apply { changed.forEach(::add) }
            preferences.putString(key, json.toString())
        }
    }

    private fun Set<String>.takeLastBounded(): Set<String> = toList().takeLast(MAX_PRESENTATION_ITEMS).toSet()

    private fun decodeSet(raw: String?): Set<String> = runCatching {
        if (raw == null) return@runCatching emptySet()
        require(raw.length <= MAX_PRESENTATION_ITEMS * (MAX_PRESENTATION_ID_LENGTH + 6))
        val array = JsonParser.parseString(raw).asJsonArray
        require(array.size() <= MAX_PRESENTATION_ITEMS)
        array.map { item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString)
            item.asString.also { require(it.isNotBlank() && it.length <= MAX_PRESENTATION_ID_LENGTH) }
        }.toSet()
    }.getOrDefault(emptySet())

    fun observeHiddenFundedDismissal(metaId: Long): Flow<String?> = observeString(hiddenFundedNoticeKey(metaId))

    fun dismissHiddenFundedNotice(metaId: Long, keys: Set<String>) {
        runCatching { preferences.putString(hiddenFundedNoticeKey(metaId), hiddenFundedFingerprint(keys)) }
    }

    fun hiddenFundedNoticeCount(keys: Set<String>, ownHides: Set<String>, dismissedFingerprint: String?): Int {
        if (keys.isEmpty() || hiddenFundedFingerprint(keys) == dismissedFingerprint) return 0
        return (keys - ownHides).size
    }

    private fun hiddenFundedFingerprint(keys: Set<String>): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(keys.sorted().joinToString("\u0000").toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Testnets stay out of the portfolio and Manage assets until the user turns them on. */
    fun observeShowTestnets(metaId: Long): Flow<Boolean> {
        return preferences.booleanFlow(showTestnetsKey(metaId), false)
            .catch { emit(false) }
    }

    fun setShowTestnets(metaId: Long, show: Boolean) {
        runCatching { preferences.putBoolean(showTestnetsKey(metaId), show) }
    }

    /**
     * Hidden funded assets the user already knows about: they answered "Keep hidden" or hid the
     * asset themselves while it held a balance. The "hidden assets hold funds" notice is shown for
     * every other hidden funded asset, so a later deposit into a hidden asset is announced again.
     */
    fun observeReviewedHiddenFundedAssets(metaId: Long): Flow<Set<String>> {
        return preferences.stringSetFlow(reviewedHiddenFundedKey(metaId)) { emptySet() }
            .map { it.toSet() }
            .catch { emit(emptySet()) }
            .distinctUntilChanged()
    }

    fun markHiddenFundedAssetsReviewed(metaId: Long, assetKeys: Set<String>) {
        if (assetKeys.isEmpty()) return
        runCatching {
            val key = reviewedHiddenFundedKey(metaId)
            val reviewed = preferences.getStringSet(key, emptySet()) + assetKeys
            // Oldest entries go first; an asset dropped here is only announced once more.
            preferences.putStringSet(key, reviewed.toList().takeLast(MAX_REMEMBERED_ASSETS).toSet())
        }
    }

    /**
     * Assets that were already hidden when this version first listed the wallet, so an earlier
     * version hid them: 4.2.0 hid every empty asset of a funded wallet and never revealed a later
     * deposit. They are labelled "Hidden in previous version" instead of looking like the user's
     * own recent choice. The snapshot is written once per wallet and never updated.
     */
    fun rememberLegacyHiddenAssets(metaId: Long, hiddenAssetKeys: () -> Set<String>) {
        runCatching {
            // A separate marker: observing the set creates an empty entry for it.
            val recordedKey = legacyHiddenRecordedKey(metaId)
            if (preferences.getBoolean(recordedKey, false)) return
            preferences.putStringSet(legacyHiddenKey(metaId), hiddenAssetKeys().take(MAX_REMEMBERED_ASSETS).toSet())
            preferences.putBoolean(recordedKey, true)
        }
    }

    fun observeLegacyHiddenAssets(metaId: Long): Flow<Set<String>> {
        return preferences.stringSetFlow(legacyHiddenKey(metaId)) { emptySet() }
            .map { it.toSet() }
            .catch { emit(emptySet()) }
            .distinctUntilChanged()
    }

    companion object {
        const val MAX_PRESENTATION_ITEMS = 256
        private const val MAX_PRESENTATION_ID_LENGTH = 256
        fun viewModeKey(metaId: Long) = "portfolio.view_mode.v1.$metaId"
        const val VALUE_NOTICE_KEY = "portfolio.value_notice_seen.v1"
        fun expandedGroupsKey(metaId: Long) = "portfolio.expanded_groups.v1.$metaId"
        fun collapsedNetworksKey(metaId: Long) = "portfolio.collapsed_networks.v1.$metaId"
        fun expandedZeroGroupsKey(metaId: Long) = "portfolio.expanded_zero_groups.v1.$metaId"
        fun zeroSummaryKey(metaId: Long) = "portfolio.zero_summary_expanded.v1.$metaId"
        fun hiddenFundedNoticeKey(metaId: Long) = "portfolio.hidden_funded_notice.v1.$metaId"
        fun manageSectionsKey(metaId: Long) = "manage_assets.collapsed_sections.v1.$metaId"

        /** A wallet rarely hides more than a handful of funded assets; the cap only bounds storage. */
        const val MAX_REMEMBERED_ASSETS = 512

        fun showTestnetsKey(metaId: Long): String = "portfolio.show_testnets.v1.$metaId"
        fun reviewedHiddenFundedKey(metaId: Long): String = "portfolio.hidden_funded_reviewed.v1.$metaId"
        fun legacyHiddenKey(metaId: Long): String = "portfolio.legacy_hidden.v1.$metaId"
        fun legacyHiddenRecordedKey(metaId: Long): String = "portfolio.legacy_hidden_recorded.v1.$metaId"

        /** One portfolio row, exactly as the database identifies it. */
        fun assetKey(chainId: ChainId, assetId: String): String = "$chainId:$assetId"
    }
}
