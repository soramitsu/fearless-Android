package jp.co.soramitsu.feature_account_impl.presentation.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_about.emailText
import kotlinx.android.synthetic.main.fragment_about.githubText
import kotlinx.android.synthetic.main.fragment_about.telegramText
import kotlinx.android.synthetic.main.fragment_about.websiteText
import kotlinx.android.synthetic.main.fragment_about.backIv

class AboutFragment : BaseFragment<AboutViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun initViews() {
        backIv.setOnClickListener { viewModel.backButtonPressed() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .aboutComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: AboutViewModel) {
        observe(viewModel.websiteLiveData, Observer {
            websiteText.text = it
        })

        observe(viewModel.versionLiveData, Observer {
            githubText.text = it
        })

        observe(viewModel.telegramLiveData, Observer {
            telegramText.text = it
        })

        observe(viewModel.emailLiveData, Observer {
            emailText.text = it
        })
    }
}