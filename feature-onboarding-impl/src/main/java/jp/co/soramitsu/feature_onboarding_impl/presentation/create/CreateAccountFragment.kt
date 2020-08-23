package jp.co.soramitsu.feature_onboarding_impl.presentation.create

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_create_account.accountNameEt
import kotlinx.android.synthetic.main.fragment_create_account.nextBtn
import kotlinx.android.synthetic.main.fragment_create_account.toolbar

class CreateAccountFragment : BaseFragment<CreateAccountViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_create_account, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        nextBtn.setOnClickListener { viewModel.nextClicked() }

        accountNameEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.accountNameChanged(s)
            }
        })
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .createAccountComponentFactory()
            .create(this)
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
                viewModel.screenshotWarningConfirmed(accountNameEt.text.toString())
            }
            .show()
    }
}