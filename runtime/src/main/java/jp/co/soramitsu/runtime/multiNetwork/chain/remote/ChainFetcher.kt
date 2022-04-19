package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import jp.co.soramitsu.commonnetworking.networkclient.SoraNetworkClient
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote

class ChainFetcher(
    private val soraNetworkClient: SoraNetworkClient
) {

    suspend fun getAssets(): List<AssetRemote> = soraNetworkClient.createJsonRequest(BuildConfig.ASSETS_URL)
}
