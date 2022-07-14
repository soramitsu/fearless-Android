package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote

class ChainFetcher(
    private val soraNetworkClient: SoramitsuNetworkClient
) {

    suspend fun getAssets() = soraNetworkClient.createJsonRequest<List<AssetRemote>>(BuildConfig.ASSETS_URL)
}
