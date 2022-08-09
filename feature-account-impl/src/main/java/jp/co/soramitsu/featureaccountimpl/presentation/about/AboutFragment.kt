package jp.co.soramitsu.featureaccountimpl.presentation.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutFragment : Fragment() {

//    private val binding by viewBinding(FragmentAboutBinding::bind)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext())
            .apply {
                setContent {
                    MaterialTheme {
                        AboutScreen()
                    }
                }
            }

//        val view = binding.root
//        binding.aboutComposeView.apply {
//            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
//            setContent {
//                MaterialTheme {
//                    Text("Hello Compose!!!")
//                }
//            }
//        }
//        return view
    }

//    override fun initViews() {
//        with(binding) {
//            backIv.setOnClickListener { viewModel.backButtonPressed() }
//            websiteWrapper.setOnClickListener { viewModel.websiteClicked() }
//            twitterWrapper.setOnClickListener { viewModel.twitterClicked() }
//            instagramWrapper.setOnClickListener { viewModel.instagramClicked() }
//            youtubeWrapper.setOnClickListener { viewModel.youtubeClicked() }
//            mediumWrapper.setOnClickListener { viewModel.mediumClicked() }
//            githubWrapper.setOnClickListener { viewModel.githubClicked() }
//            wikiWrapper.setOnClickListener { viewModel.wikiClicked() }
//            telegramWrapper.setOnClickListener { viewModel.telegramClicked() }
//            announcementWrapper.setOnClickListener { viewModel.announcementClicked() }
//            supportWrapper.setOnClickListener { viewModel.supportClicked() }
//            emailWrapper.setOnClickListener { viewModel.emailClicked() }
//            termsWrapper.setOnClickListener { viewModel.termsClicked() }
//            privacyWrapper.setOnClickListener { viewModel.privacyClicked() }
//        }
//    }
//
//    override fun inject() {
//        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
//            .aboutComponentFactory()
//            .create(this)
//            .inject(this)
//    }
//
//    override fun subscribe(viewModel: AboutViewModel) {
//        viewModel.websiteLiveData.observe {
//            binding.websiteText.text = it
//        }
//
//        viewModel.twitterLiveData.observe {
//            binding.twitterText.text = it
//        }
//
//        viewModel.youtubeLiveData.observe {
//            binding.youtubeText.text = it
//        }
//
//        viewModel.instagramLiveData.observe {
//            binding.instagramText.text = it
//        }
//
//        viewModel.mediumLiveData.observe {
//            binding.mediumText.text = it
//        }
//
//        viewModel.versionLiveData.observe {
//            binding.githubText.text = it
//        }
//
//        viewModel.wikiLiveData.observe {
//            binding.wikiText.text = it
//        }
//
//        viewModel.telegramLiveData.observe {
//            binding.telegramText.text = it
//        }
//
//        viewModel.announcementLiveData.observe {
//            binding.announcementText.text = it
//        }
//
//        viewModel.supportLiveData.observe {
//            binding.supportText.text = it
//        }
//
//        viewModel.emailLiveData.observe {
//            binding.emailText.text = it
//        }
//
//        viewModel.openSendEmailEvent.observeEvent {
//            requireContext().createSendEmailIntent(it, getString(R.string.common_email_chooser_title))
//        }
//
//        observeBrowserEvents(viewModel)
//    }
}
