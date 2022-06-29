package jp.co.soramitsu.feature_account_impl.presentation.account.create

import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.showSoftKeyboard
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentCreateAccountBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent

class CreateAccountFragment : BaseFragment<CreateAccountViewModel>(R.layout.fragment_create_account) {
    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ChainAccountCreatePayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    private val binding by viewBinding(FragmentCreateAccountBinding::bind)

    override fun initViews() {
        with(binding) {
            toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

            nextBtn.setOnClickListener {
                accountNameInput.hideSoftKeyboard()
                viewModel.nextClicked()
            }

            accountNameInput.content.onTextChanged {
                viewModel.accountNameChanged(it)
            }

            accountNameInput.content.filters = nameInputFilters()
            accountNameInput.content.requestFocus()
            accountNameInput.content.showSoftKeyboard()
        }
    }

    override fun inject() {
        val payload: ChainAccountCreatePayload? = arguments?.get(PAYLOAD_KEY) as? ChainAccountCreatePayload

        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: CreateAccountViewModel) {
        viewModel.nextButtonEnabledLiveData.observe {
            binding.nextBtn.isEnabled = it
        }

        viewModel.showScreenshotsWarningEvent.observeEvent {
            showScreenshotWarningDialog()
        }
    }

    private fun showScreenshotWarningDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.common_no_screenshot_title)
            .setMessage(R.string.common_no_screenshot_message)
            .setPositiveButton(R.string.common_ok) { dialog, _ ->
                dialog?.dismiss()
                viewModel.screenshotWarningConfirmed(binding.accountNameInput.content.text.toString())
            }
            .show()
    }
}
