package jp.co.soramitsu.feature_account_impl.presentation.account.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.account.external.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportSource
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsAddressView
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsEncryptionType
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsExport
import kotlinx.android.synthetic.main.fragment_account_details.accountDetailsNameField
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
        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        accountDetailsAddressView.setWholeClickListener {
            viewModel.addressClicked()
        }

        accountDetailsExport.setOnClickListener {
            viewModel.exportClicked()
        }

        accountDetailsNameField.content.filters = nameInputFilters()
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
        setupExternalActions(viewModel)

        viewModel.accountLiveData.observe { account ->
            accountDetailsAddressView.setMessage(account.address)
            accountDetailsAddressView.setTextIcon(account.image)

            accountDetailsEncryptionType.setMessage(account.cryptoTypeModel.name)

            accountDetailsNameField.content.setText(account.name)

            accountDetailsNode.text = account.network.name
        }

        viewModel.networkModel.observe { networkModel ->
            accountDetailsNode.setCompoundDrawablesWithIntrinsicBounds(networkModel.networkTypeUI.icon, 0, 0, 0)
        }

        viewModel.showExportSourceChooser.observeEvent(::showExportSourceChooser)

        accountDetailsNameField.content.onTextChanged(viewModel::nameChanged)
    }

    private fun showExportSourceChooser(payload: Payload<ExportSource>) {
        SourceTypeChooserBottomSheetDialog(requireActivity(), payload, viewModel::exportTypeSelected)
            .show()
    }
}