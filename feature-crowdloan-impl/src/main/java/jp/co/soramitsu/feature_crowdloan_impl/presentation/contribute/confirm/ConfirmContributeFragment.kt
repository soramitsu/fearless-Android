package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presenatation.actions.setupExternalActions
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeAmount
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeBonus
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeConfirm
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeContainer
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeFee
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeLeasingPeriod
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeOriginAcount
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeReward
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeToolbar
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

class ConfirmContributeFragment : BaseFragment<ConfirmContributeViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    companion object {

        fun getBundle(payload: ConfirmContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_contribute_confirm, container, false)
    }

    override fun initViews() {
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
    }

    override fun inject() {
        val payload = argument<ConfirmContributePayload>("KEY_PAYLOAD")

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            CrowdloanFeatureApi::class.java
        )
            .confirmContributeFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ConfirmContributeViewModel) {
        observeBrowserEvents(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(confirmContributeConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            confirmContributeAmount.setAssetBalance(it.assetBalance)
            confirmContributeAmount.setAssetName(it.tokenName)
            confirmContributeAmount.setAssetImageResource(it.tokenIconRes)
        }

        confirmContributeAmount.amountInput.setText(viewModel.selectedAmount)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(confirmContributeAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeFlow.observe(confirmContributeFee::setFeeStatus)

        with(confirmContributeReward) {
            val reward = viewModel.estimatedReward

            setVisible(reward != null)

            reward?.let { showValue(it) }
        }

        viewModel.crowdloanInfoFlow.observe {
            confirmContributeLeasingPeriod.showValue(it.leasePeriod, it.leasedUntil)
        }

        viewModel.selectedAddressModelFlow.observe {
            confirmContributeOriginAcount.setMessage(it.nameOrAddress)
            confirmContributeOriginAcount.setTextIcon(it.image)
        }

        viewModel.bonusFlow.observe {
            confirmContributeBonus.setVisible(it != null)

            it?.let(confirmContributeBonus::showValue)
        }
    }
}
