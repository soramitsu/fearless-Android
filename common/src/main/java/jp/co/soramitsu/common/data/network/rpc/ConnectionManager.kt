package jp.co.soramitsu.common.data.network.rpc

import io.reactivex.Observable

interface ConnectionManager {
    fun setAllowedToConnect(allowed: Boolean)

    fun observeAllowedToConnect(): Observable<Boolean>

    fun start(url: String)

    fun started(): Boolean

    fun switchUrl(url: String)

    fun stop()

    fun observeNetworkState(): Observable<State>
}