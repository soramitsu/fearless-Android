package jp.co.soramitsu.app.root.presentation.main

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
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

@AndroidEntryPoint
class MainFragment : BaseFragment<MainViewModel>(R.layout.fragment_main) {

    private var navController: NavController? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isEnabled = navController!!.navigateUp()
        }
    }

    private lateinit var binding: FragmentMainBinding

    override val viewModel: MainViewModel by viewModels()

    override fun onDestroyView() {
        super.onDestroyView()

        backCallback.isEnabled = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentMainBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun initViews() {
        binding.bottomNavigationView.setOnApplyWindowInsetsListener { _, insets ->
            // overwrite BottomNavigation behavior and ignore insets
            insets
        }
        binding.bottomNavigationViewWithFab.setOnApplyWindowInsetsListener { _, insets ->
            // overwrite BottomNavigation behavior and ignore insets
            insets
        }

        binding.bottomNavHost.setOnApplyWindowInsetsListener { v, insets ->
            val systemWindowInsetBottom = insets.systemWindowInsetBottom

            // post to prevent bottomNavigationView.height being 0 if callback is called before view has been measured
            v.post {
                val bottomNavFabHeight = binding.bottomNavigationViewWithFab.height
                val bottomNavHeight = binding.bottomNavigationView.height
                val useHeight = maxOf(bottomNavFabHeight, bottomNavHeight)
                val padding = (systemWindowInsetBottom - useHeight).coerceAtLeast(0)
                v.updatePadding(bottom = padding)
            }

            insets
        }

        val nestedNavHostFragment =
            childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment

        navController = nestedNavHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController!!)
        binding.bottomNavigationViewWithFab.setupWithNavController(navController!!)

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            onNavDestinationSelected(item, navController!!)
        }
        binding.bottomNavigationViewWithFab.setOnItemSelectedListener { item ->
            onNavDestinationSelected(item, navController!!)
        }

        requireActivity().onBackPressedDispatcher.addCallback(backCallback)

        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            backCallback.isEnabled = !isAtHomeTab(destination)
        }

        binding.fabMain.setOnClickListener {
            viewModel.navigateToSwapScreen()
        }
    }

    override fun subscribe(viewModel: MainViewModel) {
        viewModel.isTonAccountSelectedFlow.observe { isTon ->
            binding.fabMain.isVisible = isTon.not()
            binding.bottomNavigationViewWithFab.isVisible = isTon.not()
            binding.bottomNavigationView.isVisible = isTon
        }
    }

    private fun isAtHomeTab(destination: NavDestination) =
        destination.id == navController!!.graph.startDestinationId
}
