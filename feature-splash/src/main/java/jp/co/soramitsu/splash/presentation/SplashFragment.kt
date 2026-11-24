package jp.co.soramitsu.splash.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.splash.R

@AndroidEntryPoint
class SplashFragment : BaseFragment<SplashViewModel>() {

    override val viewModel: SplashViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun initViews() {
        // The launch art (the stacked lockup on brand ink, its crest where the system splash left it)
        // stays while the initial destination is resolved, instead of an empty page.
        if (FwRestyle.paletteEnabled) {
            view?.background = ContextCompat.getDrawable(requireContext(), jp.co.soramitsu.common.R.drawable.fw_splash_launch)
        }
    }

    override fun subscribe(viewModel: SplashViewModel) {
        viewModel.openInitialDestination()
    }
}
