package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.AmountView
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.GoNextView
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.common.view.TableCellView
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.CONTRIBUTE
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS_CONFIRM
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS_CONFIRM_SUCCESS
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.FragmentCustomContributeBinding
import jp.co.soramitsu.wallet.api.presentation.view.FeeView
import kotlinx.coroutines.flow.first

const val KEY_PAYLOAD = "KEY_PAYLOAD"

@AndroidEntryPoint
class CustomContributeFragment : BaseFragment<CustomContributeViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    @Inject
    protected lateinit var contributionManager: CustomContributeManager

    private lateinit var binding: FragmentCustomContributeBinding

    private val payload by lazy { argument<CustomContributePayload>(KEY_PAYLOAD) }

    override val viewModel: CustomContributeViewModel by viewModels()

    companion object {

        fun getBundle(payload: CustomContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCustomContributeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            customContributeContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            customContributeToolbar.setHomeButtonListener { viewModel.backClicked() }

            customContributeApply.prepareForProgress(viewLifecycleOwner)
            customContributeApply.setOnClickListener { viewModel.applyClicked() }
        }

        if (payload.parachainMetadata.isMoonbeam) {
            val title = when (payload.step) {
                TERMS, TERMS_CONFIRM_SUCCESS, CONTRIBUTE -> payload.parachainMetadata.run {
                    "$name ($token)"
                }
                TERMS_CONFIRM -> getString(R.string.common_confirm)
                else -> getString(R.string.common_bonus)
            }

            binding.customContributeToolbar.setTitle(title)
        }
    }

    override fun subscribe(viewModel: CustomContributeViewModel) {
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)

        if (payload.parachainMetadata.isMoonbeam) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        viewModel.backClicked()
                    }
                }
            )
        }

        lifecycleScope.launchWhenResumed {
            viewModel.applyButtonState.observe(viewLifecycleOwner) { (state, inProgress) ->
                when {
                    inProgress -> binding.customContributeApply.setState(ButtonState.PROGRESS)
                    state is ApplyActionState.Unavailable -> {
                        binding.customContributeApply.setState(ButtonState.DISABLED)
                        binding.customContributeApply.text = state.reason
                    }
                    state is ApplyActionState.Available -> {
                        binding.customContributeApply.setState(ButtonState.NORMAL)

                        if (payload.parachainMetadata.isMoonbeam) {
                            when (payload.step) {
                                TERMS -> binding.customContributeApply.setText(R.string.common_continue)
                                TERMS_CONFIRM -> binding.customContributeApply.setText(R.string.common_confirm)
                                TERMS_CONFIRM_SUCCESS -> binding.customContributeApply.setText(R.string.common_continue)
                                CONTRIBUTE -> binding.customContributeApply.setText(R.string.common_continue)
                                else -> binding.customContributeApply.setText(R.string.common_apply)
                            }
                        } else {
                            binding.customContributeApply.setText(R.string.common_apply)
                        }
                    }
                }
            }
        }

        viewModel.selectedAddressModelFlow.observe { address ->
            view?.findViewById<LabeledTextView>(R.id.tvMoonbeamRegistrationAccount)?.let {
                it.setMessage(address.nameOrAddress)
                it.setTextIcon(address.image)
            }
        }

        viewModel.viewStateFlow.observe { viewState ->
            binding.customFlowContainer.removeAllViews()

            val step = (viewState as? MoonbeamContributeViewState)?.customContributePayload?.step ?: TERMS
            val newView = contributionManager.createView(viewModel.customFlowType, requireContext(), step)

            binding.customFlowContainer.addView(newView)

            newView.bind(viewState, lifecycleScope)

            (viewState as? MoonbeamContributeViewState)?.enteredEtheriumAddressFlow?.first()
            view?.findViewById<TextView>(R.id.tvMoonbeamSignedHash)?.let { textView ->
                textView.setOnClickListener {
                    viewModel.signedHashClicked(textView.text.toString())
                }
            }

            viewModel.resetProgress()
        }

        viewModel.assetModelFlow.observe { model ->
            view?.findViewById<AmountView>(R.id.moonbeamContributeAmount)?.let {
                it.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

                it.setAssetBalance(model.assetBalance)
                payload.parachainMetadata.isMoonbeam
                it.setAssetName(model.tokenName)
                it.setAssetImageUrl(model.imageUrl, imageLoader)
            }
        }

        viewModel.unlockHintFlow.observe {
            view?.findViewById<TextView>(R.id.moonbeamContributeUnlockHint)?.text = it
        }

        viewModel.crowdloanDetailModelFlow.observe {
            view?.findViewById<TableCellView>(R.id.moonbeamContributeLeasingPeriod)?.showValue(
                primary = it.leasePeriod,
                secondary = getString(R.string.common_till_date, it.leasedUntil)
            )
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
                    getString(R.string.moonbeam_location_unsupported_error)
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

        viewModel.learnCrowdloanBonusModel.observe { model ->
            view?.findViewById<GoNextView>(R.id.referralLearnMore)?.let {
                it.title.paintFlags = it.title.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                it.title.text = model.text
                it.icon.load(model.iconLink, imageLoader)

                it.setOnClickListener { viewModel.learnMoreClicked() }
            }
        }
    }

    override fun buildErrorDialog(title: String, errorMessage: String): ErrorDialog {
        val buttonText = requireContext().resources.getString(jp.co.soramitsu.common.R.string.common_ok)
        val payload = AlertViewState(title, errorMessage, buttonText, textSize = 13, iconRes = jp.co.soramitsu.common.R.drawable.ic_status_warning_16)
        return ErrorDialog(payload = payload, isHideable = false, onDialogDismiss = viewModel::backClicked)
    }
}
