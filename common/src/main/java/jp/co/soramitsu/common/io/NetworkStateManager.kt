package jp.co.soramitsu.common.io

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

}

class NetworkStateListener @Inject constructor(
    private val nsm: NetworkStateManager,
) {

    private val state = MutableStateFlow(false)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            state.value = true
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            state.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternetCapability =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            state.value = hasInternetCapability
        }
    }

    fun subscribe(): Flow<Boolean> {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        nsm.cm.registerNetworkCallback(networkRequest, networkCallback)
        return state.asStateFlow()
    }

    fun release() {
        nsm.cm.unregisterNetworkCallback(networkCallback)
    }
}
