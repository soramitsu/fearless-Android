package jp.co.soramitsu.irohaconnect.api

interface IrohaConnectRouter {
    /** Opens the IrohaConnect session screen, joining the session behind [link] when one is given. */
    fun openIrohaConnect(link: String?)

    /** Leaves the IrohaConnect session screen; an active session keeps running in the wallet. */
    fun closeIrohaConnect()
}
