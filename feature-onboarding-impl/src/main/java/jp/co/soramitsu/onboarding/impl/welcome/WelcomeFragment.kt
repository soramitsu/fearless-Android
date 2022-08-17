package jp.co.soramitsu.onboarding.impl.welcome

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.databinding.FragmentWelcomeBinding
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload

@AndroidEntryPoint
class WelcomeFragment : BaseFragment<WelcomeViewModel>(R.layout.fragment_welcome) {

    companion object {
        const val KEY_PAYLOAD = "key_payload"

        fun getBundle(
            displayBack: Boolean,
            chainAccountData: ChainAccountCreatePayload? = null
        ): Bundle {
            return bundleOf(
                KEY_PAYLOAD to WelcomeFragmentPayload(displayBack, chainAccountData)
            )
        }
    }

    override val viewModel: WelcomeViewModel by viewModels()

    private val binding by viewBinding(FragmentWelcomeBinding::bind)

    override fun initViews() {
        configureTermsAndPrivacy(
            getString(R.string.onboarding_terms_and_conditions_1),
            getString(R.string.onboarding_terms_and_conditions_2),
            getString(R.string.onboarding_privacy_policy)
        )

        with(binding) {
            termsTv.movementMethod = LinkMovementMethod.getInstance()
            termsTv.highlightColor = Color.TRANSPARENT

            createAccountBtn.setOnClickListener { viewModel.createAccountClicked() }

            importAccountBtn.setOnClickListener { viewModel.importAccountClicked() }

            back.setOnClickListener { viewModel.backClicked() }
        }
    }

    private fun configureTermsAndPrivacy(sourceText: String, terms: String, privacy: String) {
        binding.termsTv.text = createSpannable(sourceText) {
            clickable(terms) {
                viewModel.termsClicked()
            }

            clickable(privacy) {
                viewModel.privacyClicked()
            }
        }
    }

    override fun subscribe(viewModel: WelcomeViewModel) {
        viewModel.shouldShowBackLiveData.observe {
            binding.back.visibility = if (it) View.VISIBLE else View.GONE
        }

        observeBrowserEvents(viewModel)
    }
}
