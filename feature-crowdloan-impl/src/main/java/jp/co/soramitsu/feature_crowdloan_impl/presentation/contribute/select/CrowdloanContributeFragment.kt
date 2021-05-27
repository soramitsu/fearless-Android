package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeAmount
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeBonus
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeBonusReward
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContainer
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeContinue
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeFee
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeLearnMore
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeLearnMoreIcon
import kotlinx.android.synthetic.main.fragment_contribute.crowdloanContributeLearnMoreTitle
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
    }

    override fun inject() {
        val payload = argument<ContributePayload>("KEY_PAYLOAD")

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

        viewModel.showNextProgress.observe(crowdloanContributeContinue::setProgress)

        viewModel.assetModelFlow.observe {
            crowdloanContributeAmount.setAssetBalance(it.assetBalance)
            crowdloanContributeAmount.setAssetName(it.tokenName)
            crowdloanContributeAmount.setAssetImageResource(it.tokenIconRes)
        }

        crowdloanContributeAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(crowdloanContributeAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(crowdloanContributeFee::setFeeStatus)

        viewModel.estimatedRewardFlow.observe { reward ->
            crowdloanContributeReward.setVisible(reward != null)

            reward?.let {
                crowdloanContributeReward.showValue(reward)
            }
        }

        viewModel.unlockHintFlow.observe(crowdloanContributeUnlockHint::setText)

        viewModel.crowdloanDetailModelFlow.observe {
            crowdloanContributeLeasingPeriod.showValue(it.leasePeriod, it.leasedUntil)
            crowdloanContributeTimeLeft.showValue(it.timeLeft)
            crowdloanContributeRaised.showValue(it.raised, it.raisedPercentage)
        }

        crowdloanContributeToolbar.setTitle(viewModel.title)

        crowdloanContributeLearnMore.setVisible(viewModel.learnCrowdloanModel != null)
        viewModel.learnCrowdloanModel?.let {
            crowdloanContributeLearnMoreTitle.text = it.text
            crowdloanContributeLearnMoreIcon.load(it.iconLink, imageLoader)
        }

        viewModel.bonusDisplayFlow.observe {
            crowdloanContributeBonus.setVisible(it != null)

            crowdloanContributeBonusReward.text = it
        }
    }
}
