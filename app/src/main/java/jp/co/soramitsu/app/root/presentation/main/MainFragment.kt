package jp.co.soramitsu.app.root.presentation.main

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.databinding.FragmentMainBinding
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.updatePadding
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseFragment<MainViewModel>(R.layout.fragment_main) {

    private var navController: NavController? = null
    private var restoringViewState = false

    @Inject
    lateinit var navigator: Navigator

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isEnabled = navController!!.navigateUp()
        }
    }

    private lateinit var binding: FragmentMainBinding

    override val viewModel: MainViewModel by viewModels()

    override fun onDestroyView() {
        navController?.let(navigator::detachMainTabs)
        super.onDestroyView()

        backCallback.isEnabled = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        restoringViewState = savedInstanceState != null
        binding = FragmentMainBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun initViews() {
        binding.bottomNavigationViewWithFab.setOnApplyWindowInsetsListener { v, insets ->
            val systemWindowInsetBottom = insets.systemWindowInsetBottom
            v.updatePadding(bottom = systemWindowInsetBottom)
            insets
        }

        val nestedNavHostFragment =
            childFragmentManager.findFragmentById(R.id.bottomNavHost) as NavHostFragment

        navController = nestedNavHostFragment.navController
        navigator.attachMainTabs(navController!!)

        binding.bottomNavigationViewWithFab.setupWithNavController(navController!!)

        binding.bottomNavigationViewWithFab.setOnItemSelectedListener { item ->
            selectMainTab(navController!!, item.itemId)
        }
        binding.bottomNavigationViewWithFab.setOnItemReselectedListener { item ->
            reselectMainTab(navController!!, item.itemId)
        }

        requireActivity().onBackPressedDispatcher.addCallback(backCallback)

        navController!!.addOnDestinationChangedListener { _, destination, _ ->
            backCallback.isEnabled = !isAtHomeTab(destination)
            binding.fabMain.isSelected = generateSequence(destination) { it.parent }
                .any { it.id == R.id.polkaswapGraph }
        }

        configurePolkaswapNavigationAction(binding.bottomNavigationViewWithFab, binding.fabMain, binding.bottomNavHost)

        if (!restoringViewState) {
            arguments?.getString(ARG_POLKAMARKT_MARKET_ID)?.let { marketId ->
                binding.bottomNavigationViewWithFab.selectedItemId = R.id.defiGraph
                navController!!.navigate(
                    R.id.polkamarktFragment,
                    bundleOf(ARG_POLKAMARKT_MARKET_ID to marketId)
                )
            }
        }
    }

    override fun subscribe(viewModel: MainViewModel) = Unit

    private fun isAtHomeTab(destination: NavDestination) = destination.id in TAB_ROOT_DESTINATIONS

    private companion object {
        const val ARG_POLKAMARKT_MARKET_ID = "marketId"

        val TAB_ROOT_DESTINATIONS = setOf(
            R.id.walletFragment,
            R.id.defiHubFragment,
            R.id.polkaswapHubFragment,
            R.id.crossChainFragment,
            R.id.profileFragment
        )
    }
}
