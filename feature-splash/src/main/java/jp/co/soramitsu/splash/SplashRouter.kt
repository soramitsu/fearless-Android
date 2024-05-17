package jp.co.soramitsu.splash

import jp.co.soramitsu.common.navigation.SecureRouter

interface SplashRouter : SecureRouter {

    fun openAddFirstAccount()

    fun openCreatePincode()

    fun openInitialCheckPincode()
}
