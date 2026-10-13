package jp.co.soramitsu.common.data.network.config

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.data.storage.Preferences

/**
 * Durable read-only and legacy rollout state, plus current verified new-capability authority.
 *
 * Discovery always runs. Its shadow switch defaults on and suppresses only automatically detected
 * review rows while balances and metadata continue to persist. Portfolio navigation stays visible
 * regardless of its legacy rollout field. XCM, the new reviewed bridge and Polkamarkt require
 * fresh signed authorization and immutable bundled approval. Historical Polkaswap and Demeter
 * keep their existing rollout behavior; a new capability token never grants their legacy paths.
 */
@Singleton
class ProductFeatureToggleStore @Inject constructor(
    private val preferences: Preferences,
    private val mutationAuthorization: MutationAuthorizationStore
) {
    fun update(config: FeatureToggleConfig) {
        preferences.putBoolean(DISCOVERY_SHADOW, config.assetDiscoveryShadowEnabled ?: true)
        preferences.putBoolean(PORTFOLIO_NAVIGATION, config.portfolioNavigationEnabled ?: true)
        preferences.putBoolean(POLKASWAP_MUTATIONS, config.polkaswapMutationsEnabled == true)
        preferences.putBoolean(DEMETER_MUTATIONS, config.demeterMutationsEnabled == true)
        // Previously persisted booleans must never grant new-capability authority.
        preferences.putBoolean(POLKAMARKT_MUTATIONS, false)
        preferences.putBoolean(XCM_MUTATIONS, false)
        preferences.putBoolean(POLKASWAP_BRIDGE_MUTATIONS, false)
        mutationAuthorization.acceptFresh(config.mutationAuthorization)
    }

    val assetDiscoveryShadowEnabled: Boolean
        get() = preferences.getBoolean(DISCOVERY_SHADOW, true)

    val portfolioNavigationEnabled: Boolean
        /** Retained for remote-config compatibility; authenticated navigation no longer reads it. */
        get() = preferences.getBoolean(PORTFOLIO_NAVIGATION, true)

    val polkaswapMutationsEnabled: Boolean
        get() = preferences.getBoolean(POLKASWAP_MUTATIONS, false)

    val demeterMutationsEnabled: Boolean
        get() = preferences.getBoolean(DEMETER_MUTATIONS, false)

    val polkamarktMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.POLKAMARKT)

    val xcmMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.XCM)

    val polkaswapBridgeMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.POLKASWAP_BRIDGE)

    private companion object {
        const val DISCOVERY_SHADOW = "feature.asset_discovery_shadow.v1"
        const val PORTFOLIO_NAVIGATION = "feature.portfolio_navigation.v1"
        const val POLKASWAP_MUTATIONS = "feature.polkaswap_mutations.v1"
        const val DEMETER_MUTATIONS = "feature.demeter_mutations.v1"
        const val POLKAMARKT_MUTATIONS = "feature.polkamarkt_mutations.v1"
        const val XCM_MUTATIONS = "feature.xcm_mutations.v1"
        const val POLKASWAP_BRIDGE_MUTATIONS = "feature.polkaswap_bridge_mutations.v1"
    }
}
