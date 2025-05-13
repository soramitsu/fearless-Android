package jp.co.soramitsu.wallet.impl.presentation.transaction.filter

import android.os.Bundle
import android.widget.CompoundButton
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bindFromMap
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionsFilterBinding
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter

@AndroidEntryPoint
class TransactionHistoryFilterFragment : BaseFragment<TransactionHistoryFilterViewModel>(R.layout.fragment_transactions_filter) {

    companion object {
        const val KEY_FILTERS_TO_SHOW = "key_filters_to_show"

        fun getBundle(filtersToShowOrAll: Set<TransactionFilter> = TransactionFilter.entries.toSet()): Bundle {
            return bundleOf(
                KEY_FILTERS_TO_SHOW to filtersToShowOrAll
            )
        }
    }

    private val binding by viewBinding(FragmentTransactionsFilterBinding::bind)

    override val viewModel: TransactionHistoryFilterViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            transactionsFilterToolbar.setHomeButtonListener { viewModel.backClicked() }

            transactionsFilterToolbar.setRightActionClickListener {
                viewModel.resetFilter()
            }

            transactionFilterApplyBtn.setOnClickListener { viewModel.applyClicked() }
        }
    }

    override fun subscribe(viewModel: TransactionHistoryFilterViewModel) {
        viewModel.isApplyButtonEnabled.observe {
            binding.transactionFilterApplyBtn.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }

        TransactionFilter.entries.minus(viewModel.usedFilters).forEach {
            when (it) {
                TransactionFilter.EXTRINSIC -> binding.transactionsFilterOtherTransactions.isGone = true
                TransactionFilter.REWARD -> binding.transactionsFilterRewards.isGone = true
                TransactionFilter.TRANSFER -> binding.transactionsFilterSwitchTransfers.isGone = true
            }
        }
        viewModel.usedFilters.forEach {
            when (it) {
                TransactionFilter.EXTRINSIC -> binding.transactionsFilterOtherTransactions.bindFilter(TransactionFilter.EXTRINSIC)
                TransactionFilter.REWARD -> binding.transactionsFilterRewards.bindFilter(TransactionFilter.REWARD)
                TransactionFilter.TRANSFER -> binding.transactionsFilterSwitchTransfers.bindFilter(TransactionFilter.TRANSFER)
            }
        }
    }

    private fun CompoundButton.bindFilter(filter: TransactionFilter) {
        bindFromMap(filter, viewModel.filtersEnabledMap, viewLifecycleOwner.lifecycleScope)
    }
}
