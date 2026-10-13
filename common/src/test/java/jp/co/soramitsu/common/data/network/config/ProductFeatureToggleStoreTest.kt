package jp.co.soramitsu.common.data.network.config

import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class ProductFeatureToggleStoreTest {

    @Test
    fun `fresh install enables read-only rollout but fails every mutation switch closed`() {
        val preferences = mock(Preferences::class.java)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenAnswer { it.getArgument(1) }
        val store = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))

        assertTrue(store.assetDiscoveryShadowEnabled)
        assertTrue(store.portfolioNavigationEnabled)
        assertFalse(store.polkaswapMutationsEnabled)
        assertFalse(store.demeterMutationsEnabled)
        assertFalse(store.polkamarktMutationsEnabled)
        assertFalse(store.xcmMutationsEnabled)
        assertFalse(store.polkaswapBridgeMutationsEnabled)
    }

    @Test
    fun `legacy remote mutation switches retain their previous rollout behavior`() {
        val preferences = mock(Preferences::class.java)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenReturn(true)
        val store = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))

        assertTrue(store.polkaswapMutationsEnabled)
        assertTrue(store.demeterMutationsEnabled)
    }

    @Test
    fun `missing remote action fields are persisted as disabled`() {
        val preferences = mock(Preferences::class.java)
        val store = ProductFeatureToggleStore(preferences, mock(MutationAuthorizationStore::class.java))

        store.update(FeatureToggleConfig(pendulumCaseEnabled = true))

        verify(preferences).putBoolean("feature.asset_discovery_shadow.v1", true)
        verify(preferences).putBoolean("feature.portfolio_navigation.v1", true)
        verify(preferences).putBoolean("feature.polkaswap_mutations.v1", false)
        verify(preferences).putBoolean("feature.demeter_mutations.v1", false)
        verify(preferences).putBoolean("feature.polkamarkt_mutations.v1", false)
        verify(preferences).putBoolean("feature.xcm_mutations.v1", false)
        verify(preferences).putBoolean("feature.polkaswap_bridge_mutations.v1", false)
    }
    @Test
    fun `cached or unsigned new capability switches cannot enable mutations`() {
        val preferences = mock(Preferences::class.java)
        `when`(preferences.getBoolean(anyString(), anyBoolean())).thenReturn(true)
        val authority = mock(MutationAuthorizationStore::class.java)
        val store = ProductFeatureToggleStore(preferences, authority)
        store.update(FeatureToggleConfig(
            polkamarktMutationsEnabled = true, xcmMutationsEnabled = true,
            polkaswapBridgeMutationsEnabled = true, mutationAuthorization = "signed-token"
        ))
        assertFalse(store.polkamarktMutationsEnabled)
        assertFalse(store.xcmMutationsEnabled)
        assertFalse(store.polkaswapBridgeMutationsEnabled)
        verify(authority).acceptFresh("signed-token")
        `when`(authority.permits(MutationCapability.XCM)).thenReturn(true)
        assertTrue(store.xcmMutationsEnabled)
        assertFalse(store.polkamarktMutationsEnabled)
    }

}
