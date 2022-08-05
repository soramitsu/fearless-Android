package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.FragmentContributeConfirmBinding
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.observeTransferChecks
import java.math.BigDecimal
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

@AndroidEntryPoint
class ConfirmContributeFragment : BaseFragment<ConfirmContributeViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentContributeConfirmBinding

    @Inject
    lateinit var factory: ConfirmContributeViewModel.ConfirmContributeViewModelFactory

    private val vm: ConfirmContributeViewModel by viewModels {
        ConfirmContributeViewModel.provideFactory(
            factory,
            argument(KEY_PAYLOAD)
        )
    }
    override val viewModel: ConfirmContributeViewModel
        get() = vm

    companion object {

        fun getBundle(payload: ConfirmContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentContributeConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            confirmContributeContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            confirmContributeToolbar.setHomeButtonListener { viewModel.backClicked() }
            confirmContributeConfirm.prepareForProgress(viewLifecycleOwner)
            confirmContributeConfirm.setOnClickListener { viewModel.nextClicked() }

            confirmContributeOriginAcount.setWholeClickListener { viewModel.originAccountClicked() }
            confirmContributeBonus.setOnClickListener { viewModel.bonusClicked() }
        }
    }

    override fun subscribe(viewModel: ConfirmContributeViewModel) {
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)
        observeTransferChecks(viewModel, viewModel::warningConfirmed)

        viewModel.showNextProgress.observe(binding.confirmContributeConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            with(binding) {
                confirmContributeAmount.setAssetBalance(it.assetBalance)
                confirmContributeAmount.setAssetName(it.tokenName)
                confirmContributeAmount.setAssetImageUrl(it.imageUrl, imageLoader)
            }
        }

        binding.confirmContributeAmount.amountInput.setText(viewModel.selectedAmount)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(binding.confirmContributeAmount::setAssetBalanceFiatAmount)
        }

        viewModel.feeFlow.observe(binding.confirmContributeFee::setFeeStatus)

        with(binding.confirmContributeReward) {
            val reward = viewModel.estimatedReward

            setVisible(reward != null)

            reward?.let { showValue(it) }
        }

        viewModel.crowdloanInfoFlow.observe {
            binding.confirmContributeLeasingPeriod.showValue(
                primary = it.leasePeriod,
                secondary = getString(R.string.common_till_date, it.leasedUntil)
            )
        }

        viewModel.selectedAddressModelFlow.observe {
            binding.confirmContributeOriginAcount.setMessage(it.nameOrAddress)
            binding.confirmContributeOriginAcount.setTextIcon(it.image)
        }

        viewModel.bonusFlow.observe {
            val isMoonbeam = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isMoonbeam == true
            val isAstar = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isAstar == true
            val isInterlay = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isInterlay == true
            binding.confirmContributeBonus.setVisible(it != null && !isMoonbeam && !isAstar && !isInterlay)

            it?.let { binding.confirmContributeBonus.showValue(it) }
        }

        viewModel.bonusNumberFlow.observe {
            binding.confirmContributeBonus.setValueColorRes(getColor(it))
        }

        viewModel.ethAddress.let {
            with(binding) {
                moonbeamEtheriumAddressText.setVisible(it != null)
                moonbeamEtheriumAddressTitle.setVisible(it != null)

                moonbeamEtheriumAddressText.text = it?.first.orEmpty()
            }
        }

        binding.confirmContributeCrowloanTitle.text = viewModel.title
        applyCustomBonus()
    }

    private fun applyCustomBonus() {
        val isApply = (argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata)?.run { isAcala || isInterlay } ?: false
        if (isApply) {
            with(binding) {
                confirmContributeBonus.setVisible(true)
                confirmContributeBonus.showValue(getString(R.string.label_link))
                confirmContributeBonus.setValueColorRes(R.color.colorAccent)
            }
        }
    }

    private fun getColor(bonus: BigDecimal?) = when {
        bonus == null || bonus <= BigDecimal.ZERO -> R.color.white
        else -> R.color.colorAccent
    }
}
