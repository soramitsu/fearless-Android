package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.style.UnderlineSpan
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.FragmentContributeBinding
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

@AndroidEntryPoint
class CrowdloanContributeFragment : BaseFragment<CrowdloanContributeViewModel>(R.layout.fragment_contribute) {

    @Inject protected lateinit var imageLoader: ImageLoader

    private val binding by viewBinding(FragmentContributeBinding::bind)

    @Inject
    lateinit var factory: CrowdloanContributeViewModel.CrowdloanContributeViewModelFactory

    private val vm: CrowdloanContributeViewModel by viewModels {
        CrowdloanContributeViewModel.provideFactory(
            factory,
            argument(KEY_PAYLOAD)
        )
    }
    override val viewModel: CrowdloanContributeViewModel
        get() = vm

    companion object {

        const val KEY_BONUS_LIVE_DATA = "KEY_BONUS_LIVE_DATA"

        fun getBundle(payload: ContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun initViews() {
        with(binding) {
            crowdloanContributeContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            crowdloanContributeToolbar.setHomeButtonListener { viewModel.backClicked() }
            crowdloanContributeContinue.prepareForProgress(viewLifecycleOwner)
            crowdloanContributeContinue.setOnClickListener { viewModel.nextClicked() }

            crowdloanContributeLearnMore.setOnClickListener { viewModel.learnMoreClicked() }

            crowdloanContributeBonus.setOnClickListener { viewModel.bonusClicked() }

            contributePrivacySwitch.bindTo(viewModel.privacyAcceptedFlow, lifecycleScope)

            contributePrivacyText.movementMethod = LinkMovementMethod.getInstance()
            contributePrivacyText.text = context?.let {
                createSpannable(it.getString(R.string.crowdloan_privacy_policy)) {
                    clickable(it.getString(R.string.about_terms)) {
                        viewModel.termsClicked()
                    }
                }
            }
            contributionTypeButton.bindTo(viewModel.contributionTypeFlow, lifecycleScope)
            contributionTypeLayout.setOnClickListener {
                contributionTypeButton.toggle()
            }
        }
    }

    override fun subscribe(viewModel: CrowdloanContributeViewModel) {
        observeRetries(viewModel)
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)

        viewModel.applyButtonState.observe { (state, isProgress) ->
            when {
                isProgress -> binding.crowdloanContributeContinue.setState(ButtonState.PROGRESS)
                state is ApplyActionState.Unavailable -> {
                    binding.crowdloanContributeContinue.setState(ButtonState.DISABLED)
                    binding.crowdloanContributeContinue.text = state.reason
                }
                state is ApplyActionState.Available -> {
                    binding.crowdloanContributeContinue.setState(ButtonState.NORMAL)
                    binding.crowdloanContributeContinue.setText(R.string.common_continue)
                }
            }
        }

        viewModel.assetModelFlow.observe {
            with(binding) {
                crowdloanContributeAmount.setAssetBalance(it.assetBalance)
                crowdloanContributeAmount.setAssetName(it.tokenName)
                crowdloanContributeAmount.setAssetImageUrl(it.imageUrl, imageLoader)
            }
        }

        binding.crowdloanContributeAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.crowdloanContributeAmount::setAssetBalanceFiatAmount)
        }

        viewModel.estimatedRewardFlow.observe { reward ->
            binding.crowdloanContributeReward.setVisible(reward != null)

            reward?.let {
                binding.crowdloanContributeReward.showValue(reward)
            }
        }

        viewModel.unlockHintFlow.observe(binding.crowdloanContributeUnlockHint::setText)

        viewModel.crowdloanDetailModelFlow.observe {
            with(binding) {
                crowdloanContributeLeasingPeriod.showValue(
                    primary = it.leasePeriod,
                    secondary = getString(R.string.common_till_date, it.leasedUntil)
                )
                crowdloanContributeTimeLeft.showValue(it.timeLeft)
                crowdloanContributeRaised.showValue(it.raised, it.raisedPercentage)
            }
        }

        binding.crowdloanContributeToolbar.setTitle(viewModel.title)
        val payload = argument<ContributePayload>(KEY_PAYLOAD)

        binding.crowdloanContributeLearnMore.setVisible(viewModel.learnCrowdloanModel != null)

        viewModel.learnCrowdloanModel?.let {
            val underlined = buildSpannedString {
                inSpans(UnderlineSpan()) { append(it.text) }
            }
            binding.crowdloanContributeLearnMore.title.text = underlined
            binding.crowdloanContributeLearnMore.icon.load(it.iconLink, imageLoader)
        }

        viewModel.bonusDisplayFlow.observe {
            binding.crowdloanContributeBonus.setVisible(it != null)

            binding.crowdloanContributeBonusReward.text = it
        }

        binding.contributeTermsLayout.setVisible(payload.parachainMetadata?.isAcala == true)
        binding.contributionTypeLayout.setVisible(payload.parachainMetadata?.isAcala == true)
    }
}
