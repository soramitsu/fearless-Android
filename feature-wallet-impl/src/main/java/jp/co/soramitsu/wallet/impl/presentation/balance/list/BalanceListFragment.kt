package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseComposeFragment
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel

@AndroidEntryPoint
class BalanceListFragment : BaseComposeFragment<BalanceListViewModel>(), BalanceListAdapter.ItemAssetHandler {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private lateinit var adapter: BalanceListAdapter

//    private val binding by viewBinding(FragmentBalanceListBinding::bind)

    override val viewModel: BalanceListViewModel by viewModels()

    @Composable
    override fun Content(padding: PaddingValues, scrollState: ScrollState) {
        FearlessTheme {
            WalletScreen(viewModel)
        }
    }

    fun initViews() {
//        binding.balanceListContent.applyInsetter {
//            type(statusBars = true) {
//                padding()
//            }
//        }

        hideKeyboard()

        adapter = BalanceListAdapter(imageLoader, this)
//        binding.balanceListAssets.adapter = adapter
//        binding.balanceListAssets.scrollToTopWhenItemsShuffled(viewLifecycleOwner)

//        with(binding) {
//            walletContainer.setOnRefreshListener {
//                viewModel.sync()
//            }
//
//            balanceListAvatar.setOnClickListener {
//                viewModel.avatarClicked()
//            }
//
//            manageAssets.setWholeClickListener {
//                viewModel.manageAssetsClicked()
//            }
//
//            balanceListTotalAmount.setOnClickListener { viewModel.onBalanceClicked() }
//            balanceListTotalAmountShimmer.setOnClickListener { viewModel.onBalanceClicked() }
//            balanceListTotalAmountEmptyShimmer.setOnClickListener { viewModel.onBalanceClicked() }
//        }
    }

/*
     fun subscribe(viewModel: BalanceListViewModel) {
        viewModel.balanceLiveData.observe {
            adapter.submitList(it.assetModels)

            with(binding) {
                balanceListTotalAmount.text = it.totalBalance?.formatAsCurrency(it.fiatSymbol)
                balanceListTotalAmountShimmerInner.text = it.totalBalance?.formatAsCurrency(it.fiatSymbol)
                balanceListTotalAmountShimmer.setVisible(it.isUpdating && it.totalBalance != null && it.isTokensUpdated, View.INVISIBLE)
                balanceListTotalAmountEmptyShimmer.setVisible(it.isUpdating && (it.totalBalance == null || !it.isTokensUpdated))
                balanceListTotalAmount.setVisible(!it.isUpdating, View.INVISIBLE)
            }
        }

        viewModel.assetsWarningLiveData.observe(binding.manageAssets.warning::setVisible)

        viewModel.currentAddressModelLiveData.observe {
            binding.balanceListTotalTitle.text = it.nameOrAddress
            binding.balanceListAvatar.setImageDrawable(it.image)
        }

        viewModel.hideRefreshEvent.observeEvent {
            binding.walletContainer.isRefreshing = false
        }

        viewModel.showFiatChooser.observeEvent(::showFiatChooser)

        viewModel.showUnsupportedChainAlert.observeEvent { showUnsupportedChainAlert() }
        viewModel.openPlayMarket.observeEvent { openPlayMarket() }
    }
*/

    private fun showFiatChooser(payload: DynamicListBottomSheet.Payload<FiatCurrency>) {
        FiatCurrenciesChooserBottomSheetDialog(requireContext(), imageLoader, payload, viewModel::onFiatSelected).show()
    }

    private fun showUnsupportedChainAlert() {
        AlertBottomSheet.Builder(requireContext())
            .setTitle(R.string.update_needed_text)
            .setMessage(R.string.chain_unsupported_text)
            .setButtonText(R.string.common_update)
            .callback { viewModel.updateAppClicked() }
            .build()
            .show()
    }

    private fun openPlayMarket() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_APP_URI)))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_MARKET_BROWSER_URI)))
        }
    }

    override fun assetClicked(asset: AssetModel) {
        viewModel.assetClicked(asset)
    }
}
