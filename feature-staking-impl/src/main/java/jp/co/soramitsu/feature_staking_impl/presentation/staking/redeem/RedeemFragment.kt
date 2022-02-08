package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_account_api.presentation.actions.setupExternalActions
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_redeem.redeemAmount
import kotlinx.android.synthetic.main.fragment_redeem.redeemConfirm
import kotlinx.android.synthetic.main.fragment_redeem.redeemContainer
import kotlinx.android.synthetic.main.fragment_redeem.redeemFee
import kotlinx.android.synthetic.main.fragment_redeem.redeemOriginAccount
import kotlinx.android.synthetic.main.fragment_redeem.redeemToolbar
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class RedeemFragment : BaseFragment<RedeemViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    companion object {

        fun getBundle(payload: RedeemPayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_redeem, container, false)
    }

    override fun initViews() {
        redeemContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        redeemToolbar.setHomeButtonListener { viewModel.backClicked() }
        redeemConfirm.prepareForProgress(viewLifecycleOwner)
        redeemConfirm.setOnClickListener { viewModel.confirmClicked() }
        redeemOriginAccount.setWholeClickListener { viewModel.originAccountClicked() }
    }

    override fun inject() {
        val payload = argument<RedeemPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .redeemFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: RedeemViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        setupExternalActions(viewModel)

        viewModel.showNextProgress.observe(redeemConfirm::setProgress)

        viewModel.assetModelLiveData.observe {
            redeemAmount.setAssetBalance(it.assetBalance)
            redeemAmount.setAssetName(it.tokenName)
            redeemAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        viewModel.amountLiveData.observe { (amount, fiatAmount) ->
            redeemAmount.amountInput.setText(amount)
            fiatAmount?.let(redeemAmount::setAssetBalanceDollarAmount)
        }

        viewModel.originAddressModelLiveData.observe {
            redeemOriginAccount.setMessage(it.nameOrAddress)
            redeemOriginAccount.setTextIcon(it.image)
        }

        viewModel.feeLiveData.observe(redeemFee::setFeeStatus)
    }
}
