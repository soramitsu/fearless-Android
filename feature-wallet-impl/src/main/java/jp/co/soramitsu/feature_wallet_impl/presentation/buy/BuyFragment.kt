package jp.co.soramitsu.feature_wallet_impl.presentation.buy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.model.BuyTokenRegistry
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.data.buyToken.WebViewProvider
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_buy.buyContent
import kotlinx.android.synthetic.main.fragment_buy.buyToolbar

class BuyFragment : BaseFragment<BuyViewModel>(), BuyTokenRegistry.Integrator.Callback {

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (buyContent.canGoBack()) {
                buyContent.goBack()
            } else {
                viewModel.backClicked()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_buy, container, false)

    override fun initViews() {
        buyToolbar.setOnClickListener {
            viewModel.backClicked()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .buyComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: BuyViewModel) {
        viewModel.buyConfigurationLiveData.observe(::integrateWithProvider)
    }

    private fun integrateWithProvider(configuration: BuyConfiguration) {
        with(configuration) {
            when (val provider = provider) {
                is WebViewProvider -> provider.createIntegrator(tokenType, address)
                    .integrate(buyContent, this@BuyFragment)
            }
        }
    }

    override fun buyCompleted() {
        viewModel.buyCompleted()
    }
}