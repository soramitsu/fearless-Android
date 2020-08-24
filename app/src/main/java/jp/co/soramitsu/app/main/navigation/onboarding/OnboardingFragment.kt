package jp.co.soramitsu.app.main.navigation.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import jp.co.soramitsu.app.R
import jp.co.soramitsu.app.main.di.MainApi
import jp.co.soramitsu.app.main.di.MainComponent
import jp.co.soramitsu.app.main.navigation.Navigator
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.interfaces.BackButtonListener
import jp.co.soramitsu.feature_onboarding_impl.presentation.welcome.WelcomeFragment
import javax.inject.Inject

class OnboardingFragment : BaseFragment<OnboardingViewModel>() {

    @Inject lateinit var navigator: Navigator

    private var navController: NavController? = null

    companion object {
        fun newInstance() = OnboardingFragment()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun initViews() {
        navController = NavHostFragment.findNavController(this)
        navigator.attachNavController(navController!!, R.navigation.onboarding_nav_graph)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainComponent>(this, MainApi::class.java)
            .onboardingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: OnboardingViewModel) {
    }

    fun onBackPressed() {
        val navHostFragment = childFragmentManager.findFragmentById(R.id.nav_host_fragment)
        navHostFragment?.childFragmentManager?.let {
            if (it.fragments.isNotEmpty()) {
                when (val currentFragment = it.fragments.last()) {
                    is BackButtonListener -> currentFragment.onBackButtonPressed()
                    is WelcomeFragment -> activity?.finish()
                    else -> navigator.popBackStack()
                }
            }
        }
    }
}