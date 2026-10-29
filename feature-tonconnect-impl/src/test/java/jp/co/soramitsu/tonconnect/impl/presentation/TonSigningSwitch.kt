package jp.co.soramitsu.tonconnect.impl.presentation

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.network.config.FeatureToggleConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.Preferences

/** The real kill-switch store over in-memory preferences: a test vetoes TON signing as a fetched config would. */
internal fun featureToggleStore(): ProductFeatureToggleStore {
    val values = mutableMapOf<String, Boolean>()
    val preferences = mockk<Preferences> {
        every { getBoolean(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { putBoolean(any(), any()) } answers { values[firstArg()] = secondArg() }
    }
    return ProductFeatureToggleStore(preferences, mockk(relaxed = true))
}

internal fun ProductFeatureToggleStore.pauseTonSigning() =
    rememberRemoteVetoes(FeatureToggleConfig(tonDappSigningEnabled = false))
