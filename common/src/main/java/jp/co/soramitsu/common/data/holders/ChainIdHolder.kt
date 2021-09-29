package jp.co.soramitsu.common.data.holders

interface ChainIdHolder {

    suspend fun chainId(): String
}
