package jp.co.soramitsu.feature_onboarding_impl.presentation.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.hideSoftKeyboard
import jp.co.soramitsu.common.utils.nameInputFilters
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_account.accountNameInput
import kotlinx.android.synthetic.main.fragment_create_account.nextBtn
import kotlinx.android.synthetic.main.fragment_create_account.toolbar

class CreateAccountFragment : BaseFragment<CreateAccountViewModel>() {

    companion object {
        private const val KEY_NETWORK_TYPE = "network_type"

        fun getBundle(networkType: Node.NetworkType?): Bundle {

            return Bundle().apply {
                putSerializable(KEY_NETWORK_TYPE, networkType)
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

        accountNameInput.content.filters = nameInputFilters()
    }

    override fun inject() {
        val networkType = argument<Node.NetworkType?>(KEY_NETWORK_TYPE)

        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this, networkType)
            .inject(this)
    }

    override fun subscribe(viewModel: CreateAccountViewModel) {
        observe(viewModel.nextButtonEnabledLiveData, Observer {
            nextBtn.isEnabled = it
        })

        observe(viewModel.showScreenshotsWarningEvent, EventObserver {
            showScreenshotWarningDialog()
        })
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