package jp.co.soramitsu.wallet.impl.presentation.send

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class SendSharedState {
    private val _assetIdToChainIdFlow = MutableStateFlow<Pair<String, ChainId>?>(null)
    private val _addressFlow = MutableStateFlow<String?>(null)

    val assetIdToChainIdFlow: Flow<Pair<String, ChainId>?> = _assetIdToChainIdFlow
    val assetIdFlow: Flow<String?> = _assetIdToChainIdFlow.map { it?.first }
    val chainIdFlow: Flow<ChainId?> = _assetIdToChainIdFlow.map { it?.second }
    val addressFlow: Flow<String?> = _addressFlow

    val assetId: String?
        get() = _assetIdToChainIdFlow.value?.first
    val chainId: ChainId?
        get() = _assetIdToChainIdFlow.value?.second

    fun update(chainId: ChainId, assetId: String) {
        _assetIdToChainIdFlow.value = assetId to chainId
    }

    fun updateAddress(address: String) {
        _addressFlow.value = address
    }

    fun clear() {
        _assetIdToChainIdFlow.value = null
        _addressFlow.value = null
    }
}
