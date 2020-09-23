package jp.co.soramitsu.app.root.presentation.main

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import jp.co.soramitsu.app.R
import kotlinx.android.synthetic.main.fragment_main.bottomNavigationView

class MainFragment : Fragment(R.layout.fragment_main) {

    private var navController: NavController? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isEnabled = navController!!.navigateUp()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nestedNavHostFragment =
            childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment

        navController = nestedNavHostFragment.navController

        bottomNavigationView.setupWithNavController(navController!!)

        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            onNavDestinationSelected(item, navController!!)
        }

        requireActivity().onBackPressedDispatcher.addCallback(backCallback)

        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            backCallback.isEnabled = !isAtHomeTab(destination)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        backCallback.isEnabled = false
    }

    private fun isAtHomeTab(destination: NavDestination) =
        destination.id == navController!!.graph.startDestination
}