package jp.co.soramitsu.account.impl.presentation.account.create

import android.os.Bundle
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

        // todo use this
        fun getBundle(isFromGoogleBackup: Boolean): Bundle {
            return bundleOf(
                CreateAccountScreenKeys.IS_FROM_GOOGLE_BACKUP_KEY to isFromGoogleBackup
            )
        }

        fun getBundle(payload: ChainAccountCreatePayload): Bundle {
            return bundleOf(CreateAccountScreenKeys.PAYLOAD_KEY to payload)
        }
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
            accountNameInput.postDelayed({ accountNameInput.content.showSoftKeyboard() }, 100)
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
            title = res.getString(R.string.common_no_screenshot_title),
            message = res.getString(R.string.common_no_screenshot_message),
            positiveButtonText = res.getString(R.string.common_ok),
            positiveClick = viewModel::screenshotWarningConfirmed
        ).show(childFragmentManager)
    }
}
