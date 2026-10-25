package jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models

sealed class ChainInfo {
    data class Simple(val chainId: String) : ChainInfo()
}
