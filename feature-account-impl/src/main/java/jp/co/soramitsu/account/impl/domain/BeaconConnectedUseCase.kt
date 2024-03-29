package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.common.data.storage.Preferences

class BeaconConnectedUseCase(private val preferences: Preferences) {

    private val beaconConnectedKey = "IS_BEACON_CONNECTED"

    operator fun invoke() = preferences.getBoolean(beaconConnectedKey, false)
}
