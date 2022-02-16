package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import kotlinx.android.synthetic.main.fragment_contribute.contributePrivacySwitch
import kotlinx.android.synthetic.main.fragment_contribute.contributePrivacyText
import kotlinx.android.synthetic.main.fragment_contribute.contributeTermsLayout
import kotlinx.android.synthetic.main.fragment_contribute.contributionTypeButton
import kotlinx.android.synthetic.main.fragment_contribute.contributionTypeLayout
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeAmount
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeBonus
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeBonusReward
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContainer
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContinue
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeLearnMore
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeLeasingPeriod
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeRaised
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeReward
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeTimeLeft
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeToolbar
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeUnlockHint
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

class CrowdloanContributeFragment : BaseFragment<CrowdloanContributeViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    companion object {

        const val KEY_BONUS_LIVE_DATA = "KEY_BONUS_LIVE_DATA"

        fun getBundle(payload: ContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_contribute, container, false)
    }

    override fun initViews() {
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

        contributePrivacyText?.movementMethod = LinkMovementMethod.getInstance()
        contributePrivacyText?.text = context?.let {
            createSpannable(it.getString(R.string.contribute_terms)) {
                clickable(it.getString(R.string.contribute_terms_clickable)) {
                    viewModel.termsClicked()
                }
            }
        }
        contributionTypeButton?.bindTo(viewModel.contributionTypeFlow, lifecycleScope)
        contributionTypeLayout?.setOnClickListener {
            contributionTypeButton?.toggle()
        }
    }

    override fun inject() {
        val payload = argument<ContributePayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            CrowdloanFeatureApi::class.java
        )
            .selectContributeFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: CrowdloanContributeViewModel) {
        observeRetries(viewModel)
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)

        viewModel.applyButtonState.observe { (state, isProgress) ->
            when {
                isProgress -> crowdloanContributeContinue.setState(ButtonState.PROGRESS)
                state is ApplyActionState.Unavailable -> {
                    crowdloanContributeContinue.setState(ButtonState.DISABLED)
                    crowdloanContributeContinue.text = state.reason
                }
                state is ApplyActionState.Available -> {
                    crowdloanContributeContinue.setState(ButtonState.NORMAL)
                    crowdloanContributeContinue.setText(R.string.common_continue)
                }
            }
        }

        viewModel.assetModelFlow.observe {
            crowdloanContributeAmount.setAssetBalance(it.assetBalance)
            crowdloanContributeAmount.setAssetName(it.tokenName)
            crowdloanContributeAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        crowdloanContributeAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(crowdloanContributeAmount::setAssetBalanceDollarAmount)
        }

        viewModel.estimatedRewardFlow.observe { reward ->
            crowdloanContributeReward.setVisible(reward != null)

            reward?.let {
                crowdloanContributeReward.showValue(reward)
            }
        }

        viewModel.unlockHintFlow.observe(crowdloanContributeUnlockHint::setText)

        viewModel.crowdloanDetailModelFlow.observe {
            crowdloanContributeLeasingPeriod.showValue(
                primary = it.leasePeriod,
                secondary = getString(R.string.common_till_date, it.leasedUntil)
            )
            crowdloanContributeTimeLeft.showValue(it.timeLeft)
            crowdloanContributeRaised.showValue(it.raised, it.raisedPercentage)
        }

        crowdloanContributeToolbar.setTitle(viewModel.title)
        val payload = argument<ContributePayload>(KEY_PAYLOAD)

        crowdloanContributeLearnMore.setVisible(viewModel.learnCrowdloanModel != null)

        viewModel.learnCrowdloanModel?.let {
            val underlined = buildSpannedString {
                inSpans(UnderlineSpan()) { append(it.text) }
            }
            crowdloanContributeLearnMore.title.text = underlined
            crowdloanContributeLearnMore.icon.load(it.iconLink, imageLoader)
        }

        viewModel.bonusDisplayFlow.observe {
            crowdloanContributeBonus.setVisible(it != null)

            crowdloanContributeBonusReward.text = it
        }

        contributeTermsLayout?.setVisible(payload.parachainMetadata?.isAcala == true)
        contributionTypeLayout?.setVisible(payload.parachainMetadata?.isAcala == true)
    }
}
