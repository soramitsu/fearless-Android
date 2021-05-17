package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_confirm_rebond.confirmRebondAmount
import kotlinx.android.synthetic.main.fragment_confirm_rebond.confirmRebondConfirm
import kotlinx.android.synthetic.main.fragment_confirm_rebond.confirmRebondFee
import kotlinx.android.synthetic.main.fragment_confirm_rebond.confirmRebondOriginAccount
import kotlinx.android.synthetic.main.fragment_confirm_rebond.confirmRebondToolbar

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmRebondFragment : BaseFragment<ConfirmRebondViewModel>() {

    companion object {

        fun getBundle(payload: ConfirmRebondPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_confirm_rebond, container, false)
    }

    override fun initViews() {
        confirmRebondToolbar.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        confirmRebondOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

        confirmRebondToolbar.setHomeButtonListener { viewModel.backClicked() }
        confirmRebondConfirm.prepareForProgress(viewLifecycleOwner)
        confirmRebondConfirm.setOnClickListener { viewModel.confirmClicked() }
    }

    override fun inject() {
        val payload = argument<ConfirmRebondPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmRebondFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmRebondViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)
        observeRetries(viewModel)

        viewModel.showNextProgress.observe(confirmRebondConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            confirmRebondAmount.setAssetBalance(it.assetBalance)
            confirmRebondAmount.setAssetName(it.tokenName)
            confirmRebondAmount.setAssetImageResource(it.tokenIconRes)
        }

        confirmRebondAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(confirmRebondAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(confirmRebondFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            confirmRebondOriginAccount.setMessage(it.nameOrAddress)
            confirmRebondOriginAccount.setTextIcon(it.image)
        }
    }
}
