package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import coil.ImageLoader
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.account.api.presentation.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.account.api.presentation.actions.copyAddressClicked
import jp.co.soramitsu.account.api.presentation.exporting.ExportSourceChooserPayload
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentBalanceDetailBinding
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy.setupBuyIntegration
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.history.showState
import javax.inject.Inject

const val KEY_ASSET_PAYLOAD = "KEY_ASSET_PAYLOAD"

@AndroidEntryPoint
class BalanceDetailFragment : BaseFragment<BalanceDetailViewModel>(R.layout.fragment_balance_detail) {

    companion object {
        fun getBundle(assetPayload: AssetPayload) = bundleOf(KEY_ASSET_PAYLOAD to assetPayload)
    }

    private val binding by viewBinding(FragmentBalanceDetailBinding::bind)

    override val viewModel: BalanceDetailViewModel by viewModels()

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun initViews() {
        hideKeyboard()

        with(binding) {
            transfersContainer.provideImageLoader(imageLoader)

            transfersContainer.initializeBehavior(anchorView = balanceDetailContent)

            transfersContainer.setScrollingListener(viewModel::transactionsScrolled)

            transfersContainer.setSlidingStateListener(::setRefreshEnabled)

            transfersContainer.setTransactionClickListener(viewModel::transactionClicked)

            transfersContainer.setFilterClickListener { viewModel.filterClicked() }

            balanceDetailContainer.setOnRefreshListener {
                viewModel.sync()
            }

            balanceDetailBack.setOnClickListener { viewModel.backClicked() }
            balanceDetailOptions.setOnClickListener {
                viewModel.accountOptionsClicked()
            }

            balanceDetaiActions.send.setOnClickListener {
                viewModel.sendClicked()
            }

            balanceDetaiActions.receive.setOnClickListener {
                viewModel.receiveClicked()
            }

            balanceDetaiActions.buy.setOnClickListener {
                viewModel.buyClicked()
            }

            balanceDetailsInfo.lockedTitle.setOnClickListener {
                viewModel.frozenInfoClicked()
            }
        }
    }

    override fun subscribe(viewModel: BalanceDetailViewModel) {
        viewModel.sync()

        viewModel.state.observe(binding.transfersContainer::showState)

        setupBuyIntegration(viewModel)

        viewModel.assetLiveData.observe { asset ->
            with(binding) {
                balanceDetailTokenIcon.load(asset.token.configuration.iconUrl, imageLoader)

                tokenBadge.setIcon(asset.token.configuration.chainIcon, imageLoader)

                balanceDetailTokenName.text = asset.token.configuration.symbol
                tokenBadge.setText(asset.token.configuration.chainName)
                balanceDetailRate.text = asset.token.fiatRate?.formatAsCurrency(asset.token.fiatSymbol) ?: ""
                balanceDetailRate.isVisible = asset.token.fiatRate != null

                asset.token.recentRateChange?.let {
                    balanceDetailRateChange.setTextColorRes(asset.token.rateChangeColorRes)
                    balanceDetailRateChange.text = it.formatAsChange()
                }
                balanceDetailRateChange.isVisible = asset.token.recentRateChange != null

                balanceDetailsInfo.total.text = asset.total.orZero().formatTokenAmount(asset.token.configuration)
                balanceDetailsInfo.totalFiat.setTextOrHide(asset.totalFiat?.formatAsCurrency(asset.token.fiatSymbol))

                balanceDetailsInfo.transferable.text = asset.available?.formatTokenAmount(asset.token.configuration)
                balanceDetailsInfo.transferableFiat.setTextOrHide(asset.availableFiat?.formatAsCurrency(asset.token.fiatSymbol))

                balanceDetailsInfo.locked.text = asset.frozen?.formatTokenAmount(asset.token.configuration)
                balanceDetailsInfo.lockedFiat.setTextOrHide(asset.frozenFiat?.formatAsCurrency(asset.token.fiatSymbol))
            }
        }

        viewModel.hideRefreshEvent.observeEvent {
            binding.balanceDetailContainer.isRefreshing = false
        }

        viewModel.showFrozenDetailsEvent.observeEvent(::showFrozenDetails)

        binding.balanceDetaiActions.buy.isEnabled = viewModel.buyEnabled

        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)

        viewModel.showAccountOptions.observeEvent(::showAccountOptions)
    }

    private fun showAccountOptions(address: String) {
        BalanceDetailOptionsBottomSheet(
            requireContext(),
            address = address,
            onExportAccount = viewModel::exportClicked,
            onSwitchNode = viewModel::switchNode,
            onCopy = viewModel::copyAddressClicked
        ).show()
    }

    private fun setRefreshEnabled(bottomSheetState: Int) {
        val bottomSheetCollapsed = BottomSheetBehavior.STATE_COLLAPSED == bottomSheetState
        binding.balanceDetailContainer.isEnabled = bottomSheetCollapsed
    }

    private fun showFrozenDetails(model: AssetModel) {
        FrozenTokensBottomSheet(requireContext(), model).show()
    }

    private fun showExportSourceChooser(payload: ExportSourceChooserPayload) {
        SourceTypeChooserBottomSheetDialog(
            titleRes = R.string.select_save_type,
            context = requireActivity(),
            payload = DynamicListBottomSheet.Payload(payload.sources),
            onClicked = { viewModel.exportTypeSelected(it, payload.chainId) }
        ).show()
    }
}
