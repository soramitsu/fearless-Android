package jp.co.soramitsu.crowdloan.impl.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId

private const val MOONBEAM_ETHERNET_ADDRESS = "MOONBEAM_ETHERNET_ADDRESS"

class CrowdloanStorage(
    private val preferences: Preferences
) {

    fun saveEthAddress(paraId: ParaId, address: String, ethAddress: String) {
        val key = getKey(paraId, address)
        val json = preferences.getString(MOONBEAM_ETHERNET_ADDRESS)
        val storedKeys = deserialize(json).toMutableMap()

        storedKeys[key] = ethAddress

        val storeValue = Gson().toJson(storedKeys)
        preferences.putString(MOONBEAM_ETHERNET_ADDRESS, storeValue)
    }

    fun getEthAddress(paraId: ParaId, address: String): String? {
        val json = preferences.getString(MOONBEAM_ETHERNET_ADDRESS)

        val map = deserialize(json)
        val key = getKey(paraId, address)

        return map[key]
    }

    private fun deserialize(json: String?) = try {
        Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type).orEmpty()
    } catch (e: Exception) {
        emptyMap()
    }

    private fun getKey(paraId: ParaId, address: String) = "$paraId#$address"
}
