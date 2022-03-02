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
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.observeTransferChecks
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeAmount
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeBonus
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeConfirm
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeContainer
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeCrowloanTitle
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeFee
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeLeasingPeriod
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeOriginAcount
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeReward
import kotlinx.android.synthetic.main.fragment_contribute_confirm.confirmContributeToolbar
import kotlinx.android.synthetic.main.fragment_contribute_confirm.moonbeamEtheriumAddressText
import kotlinx.android.synthetic.main.fragment_contribute_confirm.moonbeamEtheriumAddressTitle
import java.math.BigDecimal
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
        confirmContributeBonus?.setOnClickListener { viewModel.bonusClicked() }
    }

    override fun inject() {
        val payload = argument<ConfirmContributePayload>(KEY_PAYLOAD)

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
        observeTransferChecks(viewModel, viewModel::warningConfirmed)

        viewModel.showNextProgress.observe(confirmContributeConfirm::setProgress)

        viewModel.assetModelFlow.observe {
            confirmContributeAmount.setAssetBalance(it.assetBalance)
            confirmContributeAmount.setAssetName(it.tokenName)
            confirmContributeAmount.setAssetImageUrl(it.imageUrl, imageLoader)
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
            confirmContributeLeasingPeriod.showValue(
                primary = it.leasePeriod,
                secondary = getString(R.string.common_till_date, it.leasedUntil)
            )
        }

        viewModel.selectedAddressModelFlow.observe {
            confirmContributeOriginAcount.setMessage(it.nameOrAddress)
            confirmContributeOriginAcount.setTextIcon(it.image)
        }

        viewModel.bonusFlow.observe {
            val isMoonbeam = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isMoonbeam == true
            val isAstar = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isAstar == true
            val isInterlay = argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata?.isInterlay == true
            confirmContributeBonus?.setVisible(it != null && !isMoonbeam && !isAstar && !isInterlay)

            it?.let { confirmContributeBonus?.showValue(it) }
        }

        viewModel.bonusNumberFlow.observe {
            confirmContributeBonus?.setValueColorRes(getColor(it))
        }

        viewModel.ethAddress.let {
            moonbeamEtheriumAddressText.setVisible(it != null)
            moonbeamEtheriumAddressTitle.setVisible(it != null)

            moonbeamEtheriumAddressText.text = it?.first.orEmpty()
        }

        confirmContributeCrowloanTitle.text = viewModel.title
        applyCustomBonus()
    }

    private fun applyCustomBonus() {
        val isApply = (argument<ConfirmContributePayload>(KEY_PAYLOAD).metadata)?.run { isAcala || isInterlay } ?: false
        if (isApply) {
            confirmContributeBonus?.setVisible(true)
            confirmContributeBonus?.showValue(getString(R.string.label_link))
            confirmContributeBonus?.setValueColorRes(R.color.colorAccent)
        }
    }

    private fun getColor(bonus: BigDecimal?) = when {
        bonus == null || bonus <= BigDecimal.ZERO -> R.color.white
        else -> R.color.colorAccent
    }
}
