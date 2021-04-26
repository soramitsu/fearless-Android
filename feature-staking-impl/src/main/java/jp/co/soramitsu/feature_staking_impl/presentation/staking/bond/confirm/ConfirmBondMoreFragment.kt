package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_confirm_bond_more.confirmBondMoreAmount
import kotlinx.android.synthetic.main.fragment_confirm_bond_more.confirmBondMoreConfirm
import kotlinx.android.synthetic.main.fragment_confirm_bond_more.confirmBondMoreFee
import kotlinx.android.synthetic.main.fragment_confirm_bond_more.confirmBondMoreOriginAccount
import kotlinx.android.synthetic.main.fragment_confirm_bond_more.confirmBondMoreToolbar

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class ConfirmBondMoreFragment : BaseFragment<ConfirmBondMoreViewModel>() {

    companion object {

        fun getBundle(payload: ConfirmBondMorePayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_confirm_bond_more, container, false)
    }

    override fun initViews() {
        confirmBondMoreToolbar.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        confirmBondMoreOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }

        confirmBondMoreToolbar.setHomeButtonListener { viewModel.backClicked() }
        confirmBondMoreConfirm.prepareForProgress(viewLifecycleOwner)
        confirmBondMoreConfirm.setOnClickListener { viewModel.confirmClicked() }
    }

    override fun inject() {
        val payload = argument<ConfirmBondMorePayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .confirmBondMoreFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmBondMoreViewModel) {
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(confirmBondMoreConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            confirmBondMoreAmount.setAssetBalance(it.assetBalance)
            confirmBondMoreAmount.setAssetName(it.tokenName)
            confirmBondMoreAmount.setAssetImageResource(it.tokenIconRes)
        }

        confirmBondMoreAmount.amountInput.setText(viewModel.amount)

        viewModel.amountFiatFLow.observe {
            it?.let(confirmBondMoreAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeStatusLiveData.observe(confirmBondMoreFee::setFeeStatus)

        viewModel.originAddressModelLiveData.observe {
            confirmBondMoreOriginAccount.setMessage(it.nameOrAddress)
            confirmBondMoreOriginAccount.setTextIcon(it.image)
        }
    }
}
