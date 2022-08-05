package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import android.widget.CompoundButton
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bindFromMap
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentTransactionsFilterBinding

@AndroidEntryPoint
class TransactionHistoryFilterFragment : BaseFragment<TransactionHistoryFilterViewModel>(R.layout.fragment_transactions_filter) {

    private val binding by viewBinding(FragmentTransactionsFilterBinding::bind)

    override val viewModel: TransactionHistoryFilterViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            transactionsFilterToolbar.setHomeButtonListener { viewModel.backClicked() }

            transactionsFilterToolbar.setRightActionClickListener {
                viewModel.resetFilter()
            }

            transactionsFilterRewards.bindFilter(TransactionFilter.REWARD)
            transactionsFilterSwitchTransfers.bindFilter(TransactionFilter.TRANSFER)
            transactionsFilterOtherTransactions.bindFilter(TransactionFilter.EXTRINSIC)

            transactionFilterApplyBtn.setOnClickListener { viewModel.applyClicked() }
        }
    }

    override fun subscribe(viewModel: TransactionHistoryFilterViewModel) {
        viewModel.isApplyButtonEnabled.observe {
            binding.transactionFilterApplyBtn.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }
    }

    private fun CompoundButton.bindFilter(filter: TransactionFilter) {
        bindFromMap(filter, viewModel.filtersEnabledMap, viewLifecycleOwner.lifecycleScope)
    }
}
