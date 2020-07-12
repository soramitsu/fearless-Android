package jp.co.soramitsu.app.di.main

import jp.co.soramitsu.app.di.deps.ComponentDependencies
import jp.co.soramitsu.app.navigation.Navigator

interface MainDependencies : ComponentDependencies {

    fun navigator(): Navigator
}