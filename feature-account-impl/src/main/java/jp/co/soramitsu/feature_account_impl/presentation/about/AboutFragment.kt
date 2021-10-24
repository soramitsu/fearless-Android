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
import kotlinx.android.synthetic.main.fragment_about.*

class AboutFragment : BaseFragment<AboutViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun initViews() {
        backIv.setOnClickListener { viewModel.backButtonPressed() }
        websiteWrapper.setOnClickListener { viewModel.websiteClicked() }
        twitterWrapper.setOnClickListener { viewModel.twitterClicked() }
        youtubeWrapper.setOnClickListener { viewModel.youtubeClicked() }
        mediumWrapper.setOnClickListener { viewModel.mediumClicked() }
        githubWrapper.setOnClickListener { viewModel.githubClicked() }
        telegramWrapper.setOnClickListener { viewModel.telegramClicked() }
        announcementWrapper.setOnClickListener { viewModel.announcementClicked() }
        supportWrapper.setOnClickListener { viewModel.supportClicked() }
        emailWrapper.setOnClickListener { viewModel.emailClicked() }
        termsWrapper.setOnClickListener { viewModel.termsClicked() }
        privacyWrapper.setOnClickListener { viewModel.privacyClicked() }
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

        viewModel.twitterLiveData.observe {
            twitterText.text = it
        }

        viewModel.youtubeLiveData.observe {
            youtubeText.text = it
        }

        viewModel.mediumLiveData.observe {
            mediumText.text = it
        }

        viewModel.versionLiveData.observe {
            githubText.text = it
        }

        viewModel.telegramLiveData.observe {
            telegramText.text = it
        }

        viewModel.announcementLiveData.observe {
            announcementText.text = it
        }

        viewModel.supportLiveData.observe {
            supportText.text = it
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
