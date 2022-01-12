package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreAmount
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreContainer
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreContinue
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreFee
import kotlinx.android.synthetic.main.fragment_bond_more.bondMoreToolbar
import javax.inject.Inject

private const val PAYLOAD_KEY = "PAYLOAD_KEY"

class SelectBondMoreFragment : BaseFragment<SelectBondMoreViewModel>() {

    @Inject protected lateinit var imageLoader: ImageLoader

    companion object {

        fun getBundle(payload: SelectBondMorePayload) = Bundle().apply {
            putParcelable(PAYLOAD_KEY, payload)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bond_more, container, false)
    }

    override fun initViews() {
        bondMoreContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        bondMoreToolbar.setHomeButtonListener { viewModel.backClicked() }
        bondMoreContinue.prepareForProgress(viewLifecycleOwner)
        bondMoreContinue.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        val payload = argument<SelectBondMorePayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectBondMoreFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectBondMoreViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)

        viewModel.showNextProgress.observe(bondMoreContinue::setProgress)

        viewModel.assetModelFlow.observe {
            bondMoreAmount.setAssetBalance(it.assetBalance)
            bondMoreAmount.setAssetName(it.tokenName)
            bondMoreAmount.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        bondMoreAmount.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(bondMoreAmount::setAssetBalanceDollarAmount)
        }

        viewModel.feeLiveData.observe(bondMoreFee::setFeeStatus)
    }
}
