package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import androidx.core.os.bundleOf
import androidx.core.view.doOnNextLayout
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStakingBalanceBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond.ChooseRebondKindBottomSheet

class StakingBalanceFragment : BaseFragment<StakingBalanceViewModel>(R.layout.fragment_staking_balance) {

    companion object {
        private const val KEY_COLLATOR_ADDRESS = "collator_address"

        fun getBundle(address: String) = bundleOf(KEY_COLLATOR_ADDRESS to address)
    }

    private val binding by viewBinding(FragmentStakingBalanceBinding::bind)

    override fun initViews() {
        with(binding) {
            stakingBalanceToolbar.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            stakingBalanceToolbar.setHomeButtonListener { viewModel.backClicked() }

            stakingBalanceActions.bondMore.setOnClickListener { viewModel.bondMoreClicked() }
            stakingBalanceActions.unbond.setOnClickListener { viewModel.unbondClicked() }
            stakingBalanceActions.redeem.setOnClickListener { viewModel.redeemClicked() }

            // set padding dynamically so initially scrolling area in under toolbar
            stakingBalanceToolbar.doOnNextLayout {
                stakingBalanceSwipeRefresh.updatePadding(top = it.height + 8.dp)
            }

            stakingBalanceSwipeRefresh.setOnRefreshListener {
                viewModel.refresh()
            }

            stakingBalanceUnbondings.setMoreActionClickListener {
                viewModel.unbondingsMoreClicked()
            }
            stakingBalanceUnbondings.title.setText(R.string.staking_history_title)
        }
    }

    override fun inject() {
        val collatorAddress = arguments?.getString(KEY_COLLATOR_ADDRESS)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .stakingBalanceFactory()
            .create(this, collatorAddress)
            .inject(this)
    }

    override fun subscribe(viewModel: StakingBalanceViewModel) {
        observeValidations(viewModel)

        viewModel.redeemTitle?.let { binding.stakingBalanceActions.redeem.setText(it) }

        viewModel.stakingBalanceModelLiveData.observe {
            binding.stakingBalanceSwipeRefresh.isRefreshing = false

            with(binding.stakingBalanceInfo) {
                it.staked.titleResId?.let { bonded.setTitle(it) }
                bonded.setTokenAmount(it.staked.token)
                bonded.setFiatAmount(it.staked.fiat)

                it.unstaking.titleResId?.let { unbonding.setTitle(it) }
                unbonding.setTokenAmount(it.unstaking.token)
                unbonding.setFiatAmount(it.unstaking.fiat)

                it.redeemable.titleResId?.let { redeemable.setTitle(it) }
                redeemable.setTokenAmount(it.redeemable.token)
                redeemable.setFiatAmount(it.redeemable.fiat)
            }
        }

        viewModel.unbondingModelsLiveData.observe(binding.stakingBalanceUnbondings::submitList)
        viewModel.unbondingEnabledLiveData.observe {
            binding.stakingBalanceUnbondings.unbondingsMoreAction.isEnabled = it
        }

        viewModel.redeemEnabledLiveData.observe {
            binding.stakingBalanceActions.redeem.isEnabled = it
        }
        viewModel.shouldBlockActionButtons.observe {
            binding.stakingBalanceActions.bondMore.isEnabled = it.not()
            binding.stakingBalanceActions.unbond.isEnabled = it.not()
        }

        viewModel.showRebondActionsEvent.observeEvent {
            ChooseRebondKindBottomSheet(requireContext(), viewModel::rebondKindChosen, it)
                .show()
        }
    }
}
