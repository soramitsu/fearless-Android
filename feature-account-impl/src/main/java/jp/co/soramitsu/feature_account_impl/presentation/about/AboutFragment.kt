package jp.co.soramitsu.feature_account_impl.presentation.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_about.backIv
import kotlinx.android.synthetic.main.fragment_about.emailText
import kotlinx.android.synthetic.main.fragment_about.emailWrapper
import kotlinx.android.synthetic.main.fragment_about.githubText
import kotlinx.android.synthetic.main.fragment_about.githubWrapper
import kotlinx.android.synthetic.main.fragment_about.privacyTv
import kotlinx.android.synthetic.main.fragment_about.telegramText
import kotlinx.android.synthetic.main.fragment_about.telegramWrapper
import kotlinx.android.synthetic.main.fragment_about.termsTv
import kotlinx.android.synthetic.main.fragment_about.websiteText
import kotlinx.android.synthetic.main.fragment_about.websiteWrapper

class AboutFragment : BaseFragment<AboutViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun initViews() {
        backIv.setOnClickListener { viewModel.backButtonPressed() }
        websiteWrapper.setOnClickListener { viewModel.websiteClicked() }
        githubWrapper.setOnClickListener { viewModel.githubClicked() }
        telegramWrapper.setOnClickListener { viewModel.telegramClicked() }
        emailWrapper.setOnClickListener { viewModel.emailClicked() }
        termsTv.setOnClickListener { viewModel.termsClicked() }
        privacyTv.setOnClickListener { viewModel.privacyClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .aboutComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: AboutViewModel) {
        viewModel.websiteLiveData.observe {
            websiteText.text = it
        }

        viewModel.versionLiveData.observe {
            githubText.text = it
        }

        viewModel.telegramLiveData.observe {
            telegramText.text = it
        }

        viewModel.emailLiveData.observe {
            emailText.text = it
        }

        viewModel.openSendEmailEvent.observeEvent {
            requireContext().createSendEmailIntent(it, getString(R.string.common_email_chooser_title))
        }

        observeBrowserEvents(viewModel)
    }
}