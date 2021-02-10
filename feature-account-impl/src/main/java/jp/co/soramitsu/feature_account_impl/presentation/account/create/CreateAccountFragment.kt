package jp.co.soramitsu.feature_account_impl.presentation.account.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.mixin.api.chooseNetworkClicked
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.fragment_create_account.accountNameInput
import kotlinx.android.synthetic.main.fragment_create_account.networkInput
import kotlinx.android.synthetic.main.fragment_create_account.nextBtn
import kotlinx.android.synthetic.main.fragment_create_account.toolbar

class CreateAccountFragment : BaseFragment<CreateAccountViewModel>() {

    companion object {
        private const val KEY_FORCED_NETWORK_TYPE = "forced_network_type"

        fun getBundle(networkType: Node.NetworkType?): Bundle {

            return Bundle().apply {
                putSerializable(KEY_FORCED_NETWORK_TYPE, networkType)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_create_account, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        nextBtn.setOnClickListener {
            accountNameInput.hideSoftKeyboard()
            viewModel.nextClicked()
        }

        accountNameInput.content.onTextChanged {
            viewModel.accountNameChanged(it)
        }

        networkInput.setWholeClickListener {
            viewModel.chooseNetworkClicked()
        }

        accountNameInput.content.filters = nameInputFilters()
    }

    override fun inject() {
        val networkType = argument<Node.NetworkType?>(KEY_FORCED_NETWORK_TYPE)

        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this, networkType)
            .inject(this)
    }

    override fun subscribe(viewModel: CreateAccountViewModel) {
        viewModel.nextButtonEnabledLiveData.observe {
            nextBtn.isEnabled = it
        }

        viewModel.showScreenshotsWarningEvent.observeEvent {
            showScreenshotWarningDialog()
        }

        viewModel.selectedNetworkLiveData.observe {
            networkInput.setTextIcon(it.networkTypeUI.icon)
            networkInput.setMessage(it.name)
        }

        networkInput.isEnabled = viewModel.isNetworkTypeChangeAvailable

        viewModel.networkChooserEvent.observeEvent(::showNetworkChooser)
    }

    private fun showNetworkChooser(payload: DynamicListBottomSheet.Payload<NetworkModel>) {
        NetworkChooserBottomSheetDialog(
            requireActivity(), payload,
            viewModel.selectedNetworkLiveData::setValue
        ).show()
    }

    private fun showScreenshotWarningDialog() {
        MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.common_no_screenshot_title)
            .setMessage(R.string.common_no_screenshot_message)
            .setPositiveButton(R.string.common_ok) { dialog, _ ->
                dialog?.dismiss()
                viewModel.screenshotWarningConfirmed(accountNameInput.content.text.toString())
            }
            .show()
    }
}