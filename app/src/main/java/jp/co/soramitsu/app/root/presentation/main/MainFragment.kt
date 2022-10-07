package jp.co.soramitsu.app.root.presentation.main

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.databinding.FragmentMainBinding
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.viewBinding

@AndroidEntryPoint
class MainFragment : BaseFragment<MainViewModel>(R.layout.fragment_main) {

    private var navController: NavController? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isEnabled = navController!!.navigateUp()
        }
    }

    private val binding by viewBinding(FragmentMainBinding::bind)

    override val viewModel: MainViewModel by viewModels()

    override fun onDestroyView() {
        super.onDestroyView()

        backCallback.isEnabled = false
    }

    override fun initViews() {
        binding.bottomNavigationView.setOnApplyWindowInsetsListener { _, insets ->
            // overwrite BottomNavigation behavior and ignore insets
            insets
        }

        binding.bottomNavHost.setOnApplyWindowInsetsListener { v, insets ->
            val systemWindowInsetBottom = insets.systemWindowInsetBottom

            // post to prevent bottomNavigationView.height being 0 if callback is called before view has been measured
            v.post {
                val padding = (systemWindowInsetBottom - binding.bottomNavigationView.height).coerceAtLeast(0)
                v.updatePadding(bottom = padding)
            }

            insets
        }

        val nestedNavHostFragment =
            childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment

        navController = nestedNavHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController!!)

        binding.bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            onNavDestinationSelected(item, navController!!)
        }

        requireActivity().onBackPressedDispatcher.addCallback(backCallback)

        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            backCallback.isEnabled = !isAtHomeTab(destination)
        }
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.stakingAvailableLiveData.observe {
            binding.bottomNavigationView.menu.findItem(R.id.stakingFragment).isVisible = it
        }
    }

    private fun isAtHomeTab(destination: NavDestination) =
        destination.id == navController!!.graph.startDestinationId
}
