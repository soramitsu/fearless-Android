package jp.co.soramitsu.staking.impl.presentation.staking.balance

import androidx.core.os.bundleOf
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentStakingBalanceBinding
import jp.co.soramitsu.staking.impl.presentation.staking.balance.rebond.ChooseRebondKindBottomSheet

@AndroidEntryPoint
class StakingBalanceFragment : BaseFragment<StakingBalanceViewModel>(R.layout.fragment_staking_balance) {

    companion object {
        const val KEY_COLLATOR_ADDRESS = "collator_address"

        fun getBundle(address: String) = bundleOf(KEY_COLLATOR_ADDRESS to address)
    }

    override val viewModel: StakingBalanceViewModel by viewModels()

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

        viewModel.pendingAction.observe {
            if (it) {
                binding.stakingBalanceActions.bondMore.isEnabled = false
                binding.stakingBalanceActions.unbond.isEnabled = false
                binding.stakingBalanceActions.redeem.isEnabled = false
            } else {
                binding.stakingBalanceActions.bondMore.isEnabled = viewModel.shouldBlockStakeMore.value == false
                binding.stakingBalanceActions.unbond.isEnabled = viewModel.shouldBlockUnstake.value == false
                binding.stakingBalanceActions.redeem.isEnabled = viewModel.redeemEnabledLiveData.value == true
            }
        }

        viewModel.redeemEnabledLiveData.observe {
            binding.stakingBalanceActions.redeem.isEnabled = it
        }

        viewModel.shouldBlockStakeMore.observe {
            binding.stakingBalanceActions.bondMore.isEnabled = it.not()
        }

        viewModel.shouldBlockUnstake.observe {
            binding.stakingBalanceActions.unbond.isEnabled = it.not()
        }

        viewModel.showRebondActionsEvent.observeEvent {
            ChooseRebondKindBottomSheet(requireContext(), viewModel::rebondKindChosen, it)
                .show()
        }
    }
}
