package jp.co.soramitsu.common.data.network.config

import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ProductFeatureToggleStoreTest {

    @Test
    fun `fresh install enables shipped features and fails new capabilities closed`() {
        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))

        assertTrue(store.assetDiscoveryShadowEnabled)
        assertTrue(store.portfolioNavigationEnabled)
        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
        assertTrue(store.bitcoinSendEnabled)
        assertTrue(store.solanaSendEnabled)
        assertFalse(store.polkamarktMutationsEnabled)
        assertFalse(store.xcmMutationsEnabled)
        assertFalse(store.polkaswapBridgeMutationsEnabled)
    }

    @Test
    fun `missing kill switch fields keep shipped features enabled`() {
        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))

        store.update(FeatureToggleConfig(pendulumCaseEnabled = true))

        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
        assertTrue(store.bitcoinSendEnabled)
        assertTrue(store.solanaSendEnabled)
    }

    @Test
    fun `explicit false disables Polkaswap`() {
        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))

        store.update(FeatureToggleConfig(polkaswapMutationsEnabled = false))

        assertFalse(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
    }

    @Test
    fun `explicit false disables bitcoin and solana send independently`() {
        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))

        store.update(FeatureToggleConfig(bitcoinSendEnabled = false))
        assertFalse(store.bitcoinSendEnabled)
        assertTrue(store.solanaSendEnabled)

        store.update(FeatureToggleConfig(bitcoinSendEnabled = true, solanaSendEnabled = false))
        assertTrue(store.bitcoinSendEnabled)
        assertFalse(store.solanaSendEnabled)
    }

    @Test
    fun `veto survives fetches that omit the key until an explicit true`() {
        val preferences = mapBackedPreferences()
        val store = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))

        store.update(FeatureToggleConfig(demeterMutationsEnabled = false, solanaSendEnabled = false))
        store.update(FeatureToggleConfig())

        assertFalse(store.demeterMutationsEnabled)
        assertFalse(store.solanaSendEnabled)

        // A cold start after the veto (failed fetch: update is never called) still reads the veto.
        val restarted = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))
        assertFalse(restarted.demeterMutationsEnabled)
        assertFalse(restarted.solanaSendEnabled)

        restarted.update(FeatureToggleConfig(demeterMutationsEnabled = true, solanaSendEnabled = true))
        assertTrue(restarted.demeterMutationsEnabled)
        assertTrue(restarted.solanaSendEnabled)
    }

    @Test
    fun `refresher veto path does not touch rollout state or authority`() {
        val preferences = mapBackedPreferences()
        val authority = mock(MutationAuthorizationStore::class.java)
        val store = ProductFeatureToggleStore(preferences, authority)

        store.rememberRemoteVetoes(
            FeatureToggleConfig(
                assetDiscoveryShadowEnabled = false,
                polkaswapMutationsEnabled = false,
                bitcoinSendEnabled = false,
                mutationAuthorization = "signed-token"
            )
        )

        assertFalse(store.polkaswapMutationsEnabled)
        assertFalse(store.bitcoinSendEnabled)
        assertTrue(store.assetDiscoveryShadowEnabled)
        verify(authority, never()).acceptFresh(any())
    }

    @Test
    fun `release candidate v1 false is not read as a veto after upgrade`() {
        // RC builds persisted `remote == true`, so a missing key was stored as false under v1.
        val preferences = mapBackedPreferences(
            mutableMapOf(
                "feature.polkaswap_mutations.v1" to false,
                "feature.demeter_mutations.v1" to false
            )
        )

        // Offline first launch after the upgrade: no fetch has succeeded yet.
        val store = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))

        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
    }

    @Test
    fun `cached signed capability rules unchanged`() {
        val preferences = mapBackedPreferences(
            mutableMapOf(
                "feature.polkamarkt_mutations.v1" to true,
                "feature.xcm_mutations.v1" to true,
                "feature.polkaswap_bridge_mutations.v1" to true
            )
        )
        val authority = mock(MutationAuthorizationStore::class.java)
        val store = ProductFeatureToggleStore(preferences, authority)
        store.update(FeatureToggleConfig(
            polkamarktMutationsEnabled = true, xcmMutationsEnabled = true,
            polkaswapBridgeMutationsEnabled = true, mutationAuthorization = "signed-token"
        ))
        assertFalse(store.polkamarktMutationsEnabled)
        assertFalse(store.xcmMutationsEnabled)
        assertFalse(store.polkaswapBridgeMutationsEnabled)
        assertEquals(false, preferences.getBoolean("feature.xcm_mutations.v1", true))
        verify(authority).acceptFresh("signed-token")
        `when`(authority.permits(MutationCapability.XCM)).thenReturn(true)
        assertTrue(store.xcmMutationsEnabled)
        assertFalse(store.polkamarktMutationsEnabled)
    }

    @Test
    fun `prepared remote payload decodes to every shipped feature enabled`() {
        val config = Gson().fromJson(
            """{"pendulum_case_enabled":false,"nft_enabled":false,"dapp_enabled":false}""",
            FeatureToggleConfig::class.java
        )
        assertNull(config.polkaswapMutationsEnabled)
        assertNull(config.bitcoinSendEnabled)
        assertNull(config.solanaSendEnabled)

        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))
        store.update(config)

        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
        assertTrue(store.bitcoinSendEnabled)
        assertTrue(store.solanaSendEnabled)
    }

    @Test
    fun `emergency veto template decodes to the vetoed features only`() {
        val config = Gson().fromJson(
            """{"pendulum_case_enabled":false,"bitcoin_send_enabled":false,"solana_send_enabled":false}""",
            FeatureToggleConfig::class.java
        )
        val store = ProductFeatureToggleStore(mapBackedPreferences(), mock(MutationAuthorizationStore::class.java))

        store.update(config)

        assertFalse(store.bitcoinSendEnabled)
        assertFalse(store.solanaSendEnabled)
        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
    }

    private fun mapBackedPreferences(values: MutableMap<String, Boolean> = mutableMapOf()): Preferences {
        val preferences = mock(Preferences::class.java)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenAnswer {
            values[it.getArgument(0)] ?: it.getArgument<Boolean>(1)
        }
        `when`(preferences.contains(anyString())).thenAnswer { it.getArgument<String>(0) in values }
        doAnswer {
            values[it.getArgument(0)] = it.getArgument(1)
            null
        }.`when`(preferences).putBoolean(anyString(), anyBoolean())
        return preferences
    }
}
