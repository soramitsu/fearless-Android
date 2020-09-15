package jp.co.soramitsu.feature_account_impl.presentation.accountDetials

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.BackStyle
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsAddressView
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsName
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsNode
import kotlinx.android.synthetic.main.fragment_account_details.fearlessToolbar

private const val ACCOUNT_ADDRESS_KEY = "ACCOUNT_ADDRESS_KEY"

class AccountDetailsFragment : BaseFragment<AccountDetailsViewModel>() {

    companion object {
        fun getBundle(accountAddress: String): Bundle {
            return Bundle().apply {
                putString(ACCOUNT_ADDRESS_KEY, accountAddress)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_account_details, container, false)

    override fun initViews() {
        fearlessToolbar.setAction(R.string.common_done) {
            viewModel.backClicked()
        }

        fearlessToolbar.showBackButton(BackStyle.CROSS) {
            viewModel.backClicked()
        }

        accountDetailsAddressView.setOnCopyClickListener {
            viewModel.copyAddressClicked()
        }

        fearlessToolbar.setTitle(R.string.profile_title)
    }

    override fun inject() {
        val address = arguments!![ACCOUNT_ADDRESS_KEY] as String

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .accountDetailsComponentFactory()
            .create(this, address)
            .inject(this)
    }

    override fun subscribe(viewModel: AccountDetailsViewModel) {
        viewModel.account.observe { account ->
            accountDetailsAddressView.setAddress(account.address)

            accountDetailsName.setText(account.name)

            accountDetailsNode.text = account.network.name
        }

        viewModel.networkModel.observe { networkModel ->
            accountDetailsNode.setCompoundDrawablesWithIntrinsicBounds(networkModel.networkTypeUI.icon, 0, 0, 0)
        }
    }
}