package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeChooserPayload
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsAddressView
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsExport
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
        fearlessToolbar.setRightActionClickListener {
            viewModel.backClicked()
        }

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        accountDetailsAddressView.setOnCopyClickListener {
            viewModel.copyAddressClicked()
        }

        accountDetailsExport.setOnClickListener {
            viewModel.exportClicked()
        }
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
        viewModel.accountLiveData.observe { account ->
            accountDetailsAddressView.setAddress(account.address)

            accountDetailsName.setText(account.name)

            accountDetailsNode.text = account.network.name
        }

        viewModel.networkModel.observe { networkModel ->
            accountDetailsNode.setCompoundDrawablesWithIntrinsicBounds(networkModel.networkTypeUI.icon, 0, 0, 0)
        }

        viewModel.showExportSourceChooser.observeEvent {
            showExportSourceChooser()
        }

        accountDetailsName.onTextChanged(viewModel::nameChanged)
    }

    private fun showExportSourceChooser() {
        val sourceTypes = viewModel.exportSourceTypes
        val chooserPayload = SourceTypeChooserPayload(sourceTypes)

        SourceTypeChooserBottomSheetDialog(requireActivity(), chooserPayload) { selected ->
            viewModel.exportTypeSelected(selected)
        }
    }
}