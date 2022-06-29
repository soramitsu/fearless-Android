package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import coil.ImageLoader
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.PLAY_MARKET_APP_URI
import jp.co.soramitsu.common.PLAY_MARKET_BROWSER_URI
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.presentation.FiatCurrenciesChooserBottomSheetDialog
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.scrollToTopWhenItemsShuffled
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.FragmentBalanceListBinding
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import javax.inject.Inject

class BalanceListFragment : BaseFragment<BalanceListViewModel>(R.layout.fragment_balance_list), BalanceListAdapter.ItemAssetHandler {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private lateinit var adapter: BalanceListAdapter

    private val binding by viewBinding(FragmentBalanceListBinding::bind)

    override fun initViews() {
        binding.balanceListContent.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        hideKeyboard()

        adapter = BalanceListAdapter(imageLoader, this)
        binding.balanceListAssets.adapter = adapter
        binding.balanceListAssets.scrollToTopWhenItemsShuffled(viewLifecycleOwner)

        with(binding) {
            walletContainer.setOnRefreshListener {
                viewModel.sync()
            }

            balanceListAvatar.setOnClickListener {
                viewModel.avatarClicked()
            }

            manageAssets.setWholeClickListener {
                viewModel.manageAssetsClicked()
            }

            balanceListTotalAmount.setOnClickListener { viewModel.onBalanceClicked() }
            balanceListTotalAmountShimmer.setOnClickListener { viewModel.onBalanceClicked() }
            balanceListTotalAmountEmptyShimmer.setOnClickListener { viewModel.onBalanceClicked() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .balanceListComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: BalanceListViewModel) {
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
