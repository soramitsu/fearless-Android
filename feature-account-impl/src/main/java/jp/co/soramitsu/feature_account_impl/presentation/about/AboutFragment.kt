package jp.co.soramitsu.feature_account_impl.presentation.about

import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentAboutBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent

class AboutFragment : BaseFragment<AboutViewModel>(R.layout.fragment_about) {

    private val binding by viewBinding(FragmentAboutBinding::bind)

    override fun initViews() {
        with(binding) {
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
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .aboutComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: AboutViewModel) {
        viewModel.websiteLiveData.observe {
            binding.websiteText.text = it
        }

        viewModel.twitterLiveData.observe {
            binding.twitterText.text = it
        }

        viewModel.youtubeLiveData.observe {
            binding.youtubeText.text = it
        }

        viewModel.instagramLiveData.observe {
            binding.instagramText.text = it
        }

        viewModel.mediumLiveData.observe {
            binding.mediumText.text = it
        }

        viewModel.versionLiveData.observe {
            binding.githubText.text = it
        }

        viewModel.wikiLiveData.observe {
            binding.wikiText.text = it
        }

        viewModel.telegramLiveData.observe {
            binding.telegramText.text = it
        }

        viewModel.announcementLiveData.observe {
            binding.announcementText.text = it
        }

        viewModel.supportLiveData.observe {
            binding.supportText.text = it
        }

        viewModel.emailLiveData.observe {
            binding.emailText.text = it
        }

        viewModel.openSendEmailEvent.observeEvent {
            requireContext().createSendEmailIntent(it, getString(R.string.common_email_chooser_title))
        }

        observeBrowserEvents(viewModel)
    }
}
