package jp.co.soramitsu.feature_wallet_impl.presentation.receive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import kotlinx.android.synthetic.main.fragment_receive.accountView
import kotlinx.android.synthetic.main.fragment_receive.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_receive.qrImg

class ReceiveFragment : BaseFragment<ReceiveViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_receive, container, false)

    override fun initViews() {
        accountView.setActionListener { viewModel.addressCopyClicked() }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        fearlessToolbar.setRightActionClickListener {

        }
    }

    override fun inject() {
        FeatureUtils.getFeature<WalletFeatureComponent>(
            requireContext(),
            WalletFeatureApi::class.java
        )
            .receiveComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ReceiveViewModel) {
        viewModel.qrBitmapLiveData.observe {
            qrImg.setImageBitmap(it)
        }

        viewModel.accountLiveData.observe { account ->
            account.name?.let(accountView::setTitle)
            accountView.setText(account.address)
        }

        viewModel.accountIconLiveData.observe {
            accountView.setAccountIcon(it.image)
        }
    }
}