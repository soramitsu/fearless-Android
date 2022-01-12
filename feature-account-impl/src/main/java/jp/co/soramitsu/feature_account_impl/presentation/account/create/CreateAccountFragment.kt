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
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_account.*

class CreateAccountFragment : BaseFragment<CreateAccountViewModel>() {

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

        accountNameInput.content.filters = nameInputFilters()
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CreateAccountViewModel) {
        viewModel.nextButtonEnabledLiveData.observe {
            nextBtn.isEnabled = it
        }

        viewModel.showScreenshotsWarningEvent.observeEvent {
            showScreenshotWarningDialog()
        }
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
