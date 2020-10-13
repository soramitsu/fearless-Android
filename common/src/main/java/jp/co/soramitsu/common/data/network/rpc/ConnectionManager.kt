package jp.co.soramitsu.common.data.network.rpc

interface ConnectionManager {
    fun start(url: String)

    fun started() : Boolean

    fun switchUrl(url: String)

    fun stop()
}