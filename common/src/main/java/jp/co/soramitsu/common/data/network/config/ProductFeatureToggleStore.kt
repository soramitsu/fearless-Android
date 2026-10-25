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
 * fresh signed authorization and immutable bundled approval; a new capability token never grants
 * the shipped features below.
 *
 * Polkaswap swaps and liquidity, Demeter farming, Bitcoin send and Solana send ship enabled.
 * Their remote keys are veto-only kill switches: an explicit `false` turns the feature off and is
 * remembered across later fetches that omit the key or fail, until a successful fetch says `true`.
 * A remote `true` never enables anything beyond the compiled default.
 */
@Singleton
class ProductFeatureToggleStore @Inject constructor(
    private val preferences: Preferences,
    private val mutationAuthorization: MutationAuthorizationStore
) {
    /** Applies a successfully fetched remote config. Never call this for a failed fetch. */
    fun update(config: FeatureToggleConfig) {
        preferences.putBoolean(DISCOVERY_SHADOW, config.assetDiscoveryShadowEnabled ?: true)
        preferences.putBoolean(PORTFOLIO_NAVIGATION, config.portfolioNavigationEnabled ?: true)
        rememberRemoteVetoes(config)
        // Previously persisted booleans must never grant new-capability authority.
        preferences.putBoolean(POLKAMARKT_MUTATIONS, false)
        preferences.putBoolean(XCM_MUTATIONS, false)
        preferences.putBoolean(POLKASWAP_BRIDGE_MUTATIONS, false)
        mutationAuthorization.acceptFresh(config.mutationAuthorization)
    }

    /** Applies only the kill switches of a successfully fetched remote config. */
    fun rememberRemoteVetoes(config: FeatureToggleConfig) {
        rememberVeto(POLKASWAP_MUTATIONS, config.polkaswapMutationsEnabled)
        rememberVeto(DEMETER_MUTATIONS, config.demeterMutationsEnabled)
        rememberVeto(BITCOIN_SEND, config.bitcoinSendEnabled)
        rememberVeto(SOLANA_SEND, config.solanaSendEnabled)
    }

    val assetDiscoveryShadowEnabled: Boolean
        get() = preferences.getBoolean(DISCOVERY_SHADOW, true)

    val portfolioNavigationEnabled: Boolean
        /** Retained for remote-config compatibility; authenticated navigation no longer reads it. */
        get() = preferences.getBoolean(PORTFOLIO_NAVIGATION, true)

    val polkaswapMutationsEnabled: Boolean
        get() = isNotVetoed(POLKASWAP_MUTATIONS)

    val demeterMutationsEnabled: Boolean
        get() = isNotVetoed(DEMETER_MUTATIONS)

    val bitcoinSendEnabled: Boolean
        get() = isNotVetoed(BITCOIN_SEND)

    val solanaSendEnabled: Boolean
        get() = isNotVetoed(SOLANA_SEND)

    val polkamarktMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.POLKAMARKT)

    val xcmMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.XCM)

    val polkaswapBridgeMutationsEnabled: Boolean
        get() = mutationAuthorization.permits(MutationCapability.POLKASWAP_BRIDGE)

    // An absent key keeps the remembered state, so a veto survives until an explicit `true`.
    private fun rememberVeto(key: String, remoteValue: Boolean?) {
        if (remoteValue != null) {
            preferences.putBoolean(key, remoteValue)
        }
    }

    private fun isNotVetoed(key: String): Boolean = preferences.getBoolean(key, true)

    private companion object {
        const val DISCOVERY_SHADOW = "feature.asset_discovery_shadow.v1"
        const val PORTFOLIO_NAVIGATION = "feature.portfolio_navigation.v1"

        // v1 stored `remote == true`, so RC devices may hold a `false` that was never a veto.
        // The v2 keys hold only explicit remote values; the v1 values are ignored.
        const val POLKASWAP_MUTATIONS = "feature.polkaswap_mutations.v2"
        const val DEMETER_MUTATIONS = "feature.demeter_mutations.v2"
        const val BITCOIN_SEND = "feature.bitcoin_send.v1"
        const val SOLANA_SEND = "feature.solana_send.v1"

        const val POLKAMARKT_MUTATIONS = "feature.polkamarkt_mutations.v1"
        const val XCM_MUTATIONS = "feature.xcm_mutations.v1"
        const val POLKASWAP_BRIDGE_MUTATIONS = "feature.polkaswap_bridge_mutations.v1"
    }
}
