package jp.co.soramitsu.feature_crowdloan_impl.storage

import jp.co.soramitsu.common.data.storage.Preferences

private const val MOONBEAM_ETHERNET_ADDRESS = "MOONBEAM_ETHERNET_ADDRESS"

class CrowdloanStorage(
    private val preferences: Preferences
) {

    fun saveEthAddress(address: String) {
        preferences.putString(MOONBEAM_ETHERNET_ADDRESS, address)
    }

    fun getEthAddress() =
        preferences.getString(MOONBEAM_ETHERNET_ADDRESS)
}
