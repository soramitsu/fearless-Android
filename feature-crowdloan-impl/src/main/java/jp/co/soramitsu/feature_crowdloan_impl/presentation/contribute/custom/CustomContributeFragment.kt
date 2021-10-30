package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.AmountView
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.GoNextView
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.common.view.TableCellView
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.isMoonbeam
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_wallet_api.presentation.view.FeeView
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeApply
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeContainer
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeToolbar
import kotlinx.android.synthetic.main.fragment_custom_contribute.customFlowContainer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

class CustomContributeFragment : BaseFragment<CustomContributeViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    @Inject
    protected lateinit var contributionManager: CustomContributeManager

    private val payload by lazy { argument<CustomContributePayload>(KEY_PAYLOAD) }

    companion object {

        fun getBundle(payload: CustomContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_custom_contribute, container, false)
    }

    override fun initViews() {
        customContributeContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        customContributeToolbar.setHomeButtonListener { viewModel.backClicked() }

        customContributeApply.prepareForProgress(viewLifecycleOwner)
        customContributeApply.setOnClickListener { viewModel.applyClicked() }

//        val payload = argument<CustomContributePayload>(KEY_PAYLOAD)
        if (payload.paraId.isMoonbeam()) {
            val title = when (payload.step) {
                0, 2, 3 -> payload.parachainMetadata.run {
                    "$name ($token)"
                }
                1 -> getString(R.string.common_confirm)
                else -> getString(R.string.common_bonus)
            }

            customContributeToolbar.setTitle(title)
        }
    }

    override fun inject() {
//        val payload = argument<CustomContributePayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            CrowdloanFeatureApi::class.java
        )
            .customContributeFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: CustomContributeViewModel) {
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(customContributeApply::setProgress)

        lifecycleScope.launchWhenResumed {
            viewModel.applyButtonState.combine(viewModel.applyingInProgress) { state, inProgress ->
                when {
                    inProgress -> customContributeApply.setState(ButtonState.PROGRESS)
                    state is ApplyActionState.Unavailable -> {
                        customContributeApply.setState(ButtonState.DISABLED)
                        customContributeApply.text = state.reason
                    }
                    state is ApplyActionState.Available -> {
                        customContributeApply.setState(ButtonState.NORMAL)
//                        val payload = argument<CustomContributePayload>(KEY_PAYLOAD)
                        if (payload.paraId.isMoonbeam()) {
                            when (payload.step) {
                                0 -> customContributeApply.setText(R.string.common_continue)
                                1 -> customContributeApply.setText(R.string.common_confirm)
                                2 -> customContributeApply.setText(R.string.common_continue)
                                3 -> customContributeApply.setText(R.string.common_continue)
                            }
                        } else {
                            customContributeApply.setText(R.string.common_apply)
                        }
                    }
                }
            }.collect()
        }

        viewModel.selectedAddressModelFlow.observe { address ->
            view?.findViewById<LabeledTextView>(R.id.tvMoonbeamRegistrationAccount)?.let {
                it.setMessage(address.nameOrAddress)
                it.setTextIcon(address.image)
            }
        }

        viewModel.viewStateFlow.observe { viewState ->
            customFlowContainer.removeAllViews()

            val step = (viewState as? MoonbeamContributeViewState)?.customContributePayload?.step ?: 0
            val newView = contributionManager.createView(viewModel.customFlowType, requireContext(), step)

            customFlowContainer.addView(newView)

            newView.bind(viewState, lifecycleScope)

            (viewState as? MoonbeamContributeViewState)?.enteredEtheriumAddressFlow?.first()
        }

        viewModel.assetModelFlow.observe { model ->
            view?.findViewById<AmountView>(R.id.moonbeamContributeAmount)?.let {
                it.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

                it.setAssetBalance(model.assetBalance)
                it.setAssetName(model.tokenName)
                it.setAssetImageResource(model.tokenIconRes)
            }
        }

        viewModel.unlockHintFlow.observe {
            view?.findViewById<TextView>(R.id.moonbeamContributeUnlockHint)?.text = it
        }

        viewModel.crowdloanDetailModelFlow.observe {
            view?.findViewById<TableCellView>(R.id.moonbeamContributeLeasingPeriod)?.showValue(it.leasePeriod, it.leasedUntil)
            view?.findViewById<TableCellView>(R.id.moonbeamContributeTimeLeft)?.showValue(it.timeLeft)
            view?.findViewById<TableCellView>(R.id.moonbeamContributeRaised)?.showValue(it.raised, it.raisedPercentage)
        }

        viewModel.estimatedRewardFlow.observe { reward ->
            view?.findViewById<TableCellView>(R.id.moonbeamContributeReward)?.let { view ->
                view.setVisible(reward != null)
                reward?.let { view.showValue(reward) }
            }
        }

        viewModel.feeLive.observe {
            view?.findViewById<FeeView>(R.id.moonbeamRegistrationFee)?.setFeeStatus(it)
        }

        viewModel.healthFlow.observe { isHealth ->
            if (isHealth.not()) {
                viewModel.showError(
                    getString(R.string.moonbeam_ineligible_to_participate)
                )
            }
        }

        viewModel.learnCrowdloanModel.observe { model ->
            view?.findViewById<GoNextView>(R.id.moonbeamContributeLearnMore)?.let {
                it.title.text = model.text
                it.icon.load(model.iconLink, imageLoader)

                it.setOnClickListener { viewModel.learnMoreClicked() }
            }
        }
    }
}
