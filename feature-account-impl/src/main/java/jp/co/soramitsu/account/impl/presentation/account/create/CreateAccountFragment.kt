package jp.co.soramitsu.account.impl.presentation.account.create

import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.showSoftKeyboard
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentCreateAccountBinding

@AndroidEntryPoint
class CreateAccountFragment : BaseFragment<CreateAccountViewModel>(R.layout.fragment_create_account) {
    companion object {
        const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(payload: ChainAccountCreatePayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    private val binding by viewBinding(FragmentCreateAccountBinding::bind)

    override val viewModel: CreateAccountViewModel by viewModels()

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

    override fun subscribe(viewModel: CreateAccountViewModel) {
        viewModel.nextButtonEnabledLiveData.observe {
            binding.nextBtn.isEnabled = it
        }

        viewModel.showScreenshotsWarningEvent.observeEvent {
            showScreenshotWarningDialog()
        }
    }

    private fun showScreenshotWarningDialog() {
        val res = requireContext()
        ErrorDialog(
            title = res.getString(jp.co.soramitsu.common.R.string.common_no_screenshot_title),
            message = res.getString(jp.co.soramitsu.common.R.string.common_no_screenshot_message),
            positiveButtonText = res.getString(jp.co.soramitsu.common.R.string.common_ok),
            positiveClick = { viewModel.screenshotWarningConfirmed(binding.accountNameInput.content.text.toString()) }
        ).show(childFragmentManager)
    }
}
