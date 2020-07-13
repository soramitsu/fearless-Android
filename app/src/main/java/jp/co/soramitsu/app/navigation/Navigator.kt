package jp.co.soramitsu.app.navigation

import android.content.Context
import androidx.navigation.NavController
import jp.co.soramitsu.app.MainActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.users.UsersRouter
import jp.co.soramitsu.users.presentation.details.UserFragment

class Navigator : UsersRouter, SplashRouter {

    private var navController: NavController? = null

    fun attachNavController(navController: NavController, graph: Int) {
        navController.setGraph(graph)
        this.navController = navController
    }

    fun detachNavController(navController: NavController) {
        if (this.navController == navController) {
            this.navController = null
        }
    }

    override fun openUser(userId: Int) {
        navController?.navigate(R.id.userFragment, UserFragment.createBundle(userId))
    }

    override fun returnToUsers() {
        navController?.popBackStack()
    }

    override fun openMain(context: Context) {
        MainActivity.start(context)
    }
}