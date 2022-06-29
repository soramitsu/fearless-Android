package jp.co.soramitsu.feature_staking_impl.presentation.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.mixin.impl.observeRetries
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.setProgress
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.common.rewardDestination.observeRewardDestinationChooser
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeViews
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.displayFeeStatus
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingAmountField
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingContainer
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingFeeFiat
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingFeeProgress
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingFeeToken
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingNext
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingPayoutViewer
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingRewardDestinationChooser
import kotlinx.android.synthetic.main.fragment_setup_staking.setupStakingToolbar

class SetupStakingFragment : BaseFragment<SetupStakingViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_setup_staking, container, false)
    }

    override fun initViews() {
        setupStakingContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        setupStakingToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        setupStakingNext.prepareForProgress(viewLifecycleOwner)
        setupStakingNext.setOnClickListener { viewModel.nextClicked() }
        setupStakingPayoutViewer.setOnViewMoreClickListener { viewModel.learnMoreClicked(viewModel) }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .setupStakingComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SetupStakingViewModel) {
        observeRetries(viewModel)
        observeValidations(viewModel)
        observeBrowserEvents(viewModel)
        observeRewardDestinationChooser(viewModel, setupStakingRewardDestinationChooser)

        viewModel.showNextProgress.observe(setupStakingNext::setProgress)

        viewModel.assetModelsFlow.observe {
            setupStakingAmountField.setAssetBalance(it.assetBalance)
            setupStakingAmountField.setAssetName(it.tokenName)
            setupStakingAmountField.setAssetImageUrl(it.imageUrl, imageLoader)
        }

        setupStakingAmountField.amountInput.bindTo(viewModel.enteredAmountFlow, lifecycleScope)

        viewModel.enteredFiatAmountFlow.observe {
            it?.let(setupStakingAmountField::setAssetBalanceFiatAmount)
        }

        viewModel.feeLiveData.observe {
            displayFeeStatus(
                it,
                FeeViews(setupStakingFeeProgress, setupStakingFeeFiat, setupStakingFeeToken)
            )
        }

        viewModel.showMinimumStakeAlert.observeEvent {
            AlertBottomSheet.Builder(requireContext())
                .setTitle(R.string.staking_inactive_bond)
                .setMessage(resources.getString(R.string.min_staking_warning_text, it))
                .setButtonText(R.string.common_confirm)
                .callback { viewModel.minimumStakeConfirmed() }
                .build()
                .show()
        }

        viewModel.currentAccountAddressModel.observe {
            setupStakingPayoutViewer.setAccountInfo(it)
        }

        viewModel.rewardReturnsLiveData.observe {
            setupStakingPayoutViewer.setRewardEstimation(it.payout)
        }

        viewModel.currentStakingType.observe {
            setupStakingRewardDestinationChooser.isVisible = it == Chain.Asset.StakingType.RELAYCHAIN
            setupStakingPayoutViewer.isVisible = it == Chain.Asset.StakingType.PARACHAIN
        }
    }
}
