package jp.co.soramitsu.common.data.network.config

import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

/**
 * Upgrade continuity against the committed production `feature_toggle`
 * (`remote-config/feature_toggle.production.json`, byte-for-byte what
 * shared-features-utils master serves for 4.2.0; it has no capability keys).
 *
 * Every shipped fund-moving feature (Polkaswap swap and liquidity, Demeter farming, Bitcoin and
 * Solana send) must stay available to upgraders from any version, online or offline, and only an
 * explicit remote `false` may pause one (veto-only, safety invariant 7).
 */
class FeatureContinuityTest {

    private val productionPayload: FeatureToggleConfig = Gson().fromJson(
        requireNotNull(javaClass.getResourceAsStream("/remote-config/feature_toggle.production.json")) {
            "remote-config/feature_toggle.production.json is missing from test resources"
        }.reader().use { it.readText() },
        FeatureToggleConfig::class.java
    )

    @Test
    fun `4_2_0 upgrader keeps every shipped feature with the production payload`() {
        // 4.2.0 wrote no feature keys at all.
        val store = store(mutableMapOf())

        store.update(productionPayload)

        store.assertShippedFeaturesEnabled()
        store.assertNewCapabilitiesClosed()
    }

    @Test
    fun `4_2_0 upgrader keeps every shipped feature on an offline first launch`() {
        val store = store(mutableMapOf())

        store.assertShippedFeaturesEnabled()
        store.assertNewCapabilitiesClosed()
    }

    @Test
    fun `release candidate tester keeps swap and farming online and offline`() {
        // RC builds (DB 76-80) persisted `remote == true`, so the absent keys became false (T-13).
        val values = mutableMapOf<String, Boolean>(
            "feature.polkaswap_mutations.v1" to false,
            "feature.demeter_mutations.v1" to false,
            "feature.xcm_mutations.v1" to true
        )

        val offline = store(values)
        offline.assertShippedFeaturesEnabled()
        offline.assertNewCapabilitiesClosed()

        val online = store(values)
        online.update(productionPayload)
        online.assertShippedFeaturesEnabled()
        online.assertNewCapabilitiesClosed()
    }

    @Test
    fun `a veto holds through the production payload and failed refreshes until an explicit true`() = runTest {
        val values = mutableMapOf<String, Boolean>()
        val store = store(values)
        var served: () -> FeatureToggleConfig = {
            FeatureToggleConfig(polkaswapMutationsEnabled = false, bitcoinSendEnabled = false)
        }
        val fetcher = mock<RemoteConfigFetcher> {
            onBlocking { getFeatureToggle(any()) } doAnswer { served() }
        }
        val refresher = MutationAuthorizationRefresher(fetcher, mock(), store)

        refresher.refreshOnce()
        assertFalse(store.polkaswapMutationsEnabled)
        assertFalse(store.bitcoinSendEnabled)

        // The emergency key is removed again: devices that saw the veto stay paused.
        served = { productionPayload }
        refresher.refreshOnce()
        store.update(productionPayload)
        assertFalse(store.polkaswapMutationsEnabled)
        assertFalse(store.bitcoinSendEnabled)

        served = { throw IllegalStateException("offline") }
        refresher.refreshOnce()
        assertFalse(store(values).polkaswapMutationsEnabled)
        assertFalse(store(values).bitcoinSendEnabled)

        // The features nobody vetoed were never affected.
        assertTrue(store.demeterMutationsEnabled)
        assertTrue(store.solanaSendEnabled)

        served = { FeatureToggleConfig(polkaswapMutationsEnabled = true, bitcoinSendEnabled = true) }
        refresher.refreshOnce()
        store.assertShippedFeaturesEnabled()
    }

    @Test
    fun `remote true never grants a capability the build ships off`() {
        val store = store(mutableMapOf())

        store.update(
            FeatureToggleConfig(
                xcmMutationsEnabled = true,
                polkamarktMutationsEnabled = true,
                polkaswapBridgeMutationsEnabled = true
            )
        )

        store.assertNewCapabilitiesClosed()
    }

    private fun ProductFeatureToggleStore.assertShippedFeaturesEnabled() {
        assertTrue(polkaswapMutationsEnabled)
        assertTrue(demeterMutationsEnabled)
        assertTrue(bitcoinSendEnabled)
        assertTrue(solanaSendEnabled)
    }

    private fun ProductFeatureToggleStore.assertNewCapabilitiesClosed() {
        assertFalse(xcmMutationsEnabled)
        assertFalse(polkamarktMutationsEnabled)
        assertFalse(polkaswapBridgeMutationsEnabled)
    }

    // An unsigned authority store: signed capability tokens are out of scope here.
    private fun store(values: MutableMap<String, Boolean>) =
        ProductFeatureToggleStore(mapBackedPreferences(values), mock())

    private fun mapBackedPreferences(values: MutableMap<String, Boolean>): Preferences = mock {
        on { getBoolean(any(), any()) } doAnswer { values[it.getArgument(0)] ?: it.getArgument<Boolean>(1) }
        on { contains(any()) } doAnswer { it.getArgument<String>(0) in values }
        on { putBoolean(any(), any()) } doAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            Unit
        }
    }
}
