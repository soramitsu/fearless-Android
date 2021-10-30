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
import kotlinx.android.synthetic.main.fragment_about.announcementText
import kotlinx.android.synthetic.main.fragment_about.announcementWrapper
import kotlinx.android.synthetic.main.fragment_about.backIv
import kotlinx.android.synthetic.main.fragment_about.emailText
import kotlinx.android.synthetic.main.fragment_about.emailWrapper
import kotlinx.android.synthetic.main.fragment_about.githubText
import kotlinx.android.synthetic.main.fragment_about.githubWrapper
import kotlinx.android.synthetic.main.fragment_about.instagramText
import kotlinx.android.synthetic.main.fragment_about.instagramWrapper
import kotlinx.android.synthetic.main.fragment_about.mediumText
import kotlinx.android.synthetic.main.fragment_about.mediumWrapper
import kotlinx.android.synthetic.main.fragment_about.privacyWrapper
import kotlinx.android.synthetic.main.fragment_about.supportText
import kotlinx.android.synthetic.main.fragment_about.supportWrapper
import kotlinx.android.synthetic.main.fragment_about.telegramText
import kotlinx.android.synthetic.main.fragment_about.telegramWrapper
import kotlinx.android.synthetic.main.fragment_about.termsWrapper
import kotlinx.android.synthetic.main.fragment_about.twitterText
import kotlinx.android.synthetic.main.fragment_about.twitterWrapper
import kotlinx.android.synthetic.main.fragment_about.websiteText
import kotlinx.android.synthetic.main.fragment_about.websiteWrapper
import kotlinx.android.synthetic.main.fragment_about.wikiText
import kotlinx.android.synthetic.main.fragment_about.wikiWrapper
import kotlinx.android.synthetic.main.fragment_about.youtubeText
import kotlinx.android.synthetic.main.fragment_about.youtubeWrapper

class AboutFragment : BaseFragment<AboutViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun initViews() {
        backIv.setOnClickListener { viewModel.backButtonPressed() }
        websiteWrapper.setOnClickListener { viewModel.websiteClicked() }
        twitterWrapper.setOnClickListener { viewModel.twitterClicked() }
        instagramWrapper.setOnClickListener { viewModel.instagramClicked() }
        youtubeWrapper.setOnClickListener { viewModel.youtubeClicked() }
        mediumWrapper.setOnClickListener { viewModel.mediumClicked() }
        githubWrapper.setOnClickListener { viewModel.githubClicked() }
        wikiWrapper.setOnClickListener { viewModel.wikiClicked() }
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

        viewModel.instagramLiveData.observe {
            instagramText.text = it
        }

        viewModel.mediumLiveData.observe {
            mediumText.text = it
        }

        viewModel.versionLiveData.observe {
            githubText.text = it
        }

        viewModel.wikiLiveData.observe {
            wikiText.text = it
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
