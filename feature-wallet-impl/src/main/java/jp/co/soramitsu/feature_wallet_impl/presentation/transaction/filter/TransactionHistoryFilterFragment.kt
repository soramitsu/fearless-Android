package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.lifecycle.lifecycleScope
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.ExtrinsicFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.RewardFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.TransferFilter
import kotlinx.android.synthetic.main.fragment_transactions_filter.*
import kotlinx.coroutines.flow.MutableStateFlow

class TransactionHistoryFilterFragment : BaseFragment<TransactionHistoryFilterViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_transactions_filter, container, false)

    override fun initViews() {
        transactionsFilterToolbar.setHomeButtonListener { viewModel.backClicked() }

        transactionsFilterToolbar.setRightActionClickListener {
            viewModel.resetFilter()
        }

        transactionsFilterRewards.bindFilter(RewardFilter::class.java)
        transactionsFilterSwitchTransfers.bindFilter(TransferFilter::class.java)
        transactionsFilterOtherTransactions.bindFilter(ExtrinsicFilter::class.java)

        transactionFilterApplyBtn.setOnClickListener { viewModel.applyClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        ).transactionHistoryComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: TransactionHistoryFilterViewModel) {
        viewModel.isApplyButtonEnabled.observe {
            transactionFilterApplyBtn.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }
    }

    private fun CompoundButton.bindFilter(filterClass: Class<out HistoryFilter>) {
        bondFromMap(filterClass, viewModel.filtersEnabledMap)
    }

    private fun <T> CompoundButton.bondFromMap(key: Class<out T>, map: Map<out Class<out T>, MutableStateFlow<Boolean>>) {
        val source = map[key] ?: error("Cannot find $key source")

        bindTo(source, lifecycleScope)
    }
}
