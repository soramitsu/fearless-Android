package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.interfaces.BackButtonListener
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.feature_account_impl.presentation.pincode.view.DotsProgressView
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import kotlinx.android.synthetic.main.fragment_pincode.dotsProgressView
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeTitleTv
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeView
import javax.inject.Inject

class PincodeFragment : BaseFragment<PinCodeViewModel>(), BackButtonListener {

    @Inject lateinit var fingerprintWrapper: FingerprintWrapper
    private lateinit var fingerprintDialog: BottomSheetDialog

    companion object {
        const val PINCODE_ACTION_KEY = "pincode_action"

        fun getBundleForCreatePincode(): Bundle {
            return Bundle().apply {
                putSerializable(PINCODE_ACTION_KEY, PinCodeAction.CREATE_PIN_CODE)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pincode, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(context!!, AccountFeatureApi::class.java)
            .pincodeMnemonicComponentFactory()
            .create(DotsProgressView.MAX_PROGRESS, this)
            .inject(this)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.backPressed() }

        fingerprintDialog = BottomSheetDialog(activity!!).apply {
            setContentView(R.layout.bottom_sheet_fingerprint_dialog)
            setCancelable(true)
            setOnCancelListener { fingerprintWrapper.cancel() }
            findViewById<TextView>(R.id.btnCancel)?.setOnClickListener { fingerprintWrapper.cancel() }
        }

        viewModel.fingerprintScannerAvailable(fingerprintWrapper.isAuthReady())

        with(pinCodeView) {
            pinCodeListener = { viewModel.pinCodeNumberClicked(it) }
            deleteClickListener = { viewModel.pinCodeDeleteClicked() }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        observe(viewModel.startFingerprintScannerEventLiveData, EventObserver {
            if (fingerprintWrapper.isAuthReady()) {
                fingerprintWrapper.startAuth()
            }
        })

        observe(viewModel.biometricSwitchDialogLiveData, EventObserver {
            MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(R.string.pincode_fingerprint_switch_dialog_title)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    viewModel.fingerprintSwitchDialogYesClicked()
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    viewModel.fingerprintSwitchDialogNoClicked()
                }
                .show()
        })

        observe(viewModel.showFingerPrintEventLiveData, EventObserver {
            pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady())
        })

        observe(viewModel.toolbarTitleResLiveData, Observer {
            pinCodeTitleTv.setText(it)
        })

        observe(viewModel.fingerPrintDialogVisibilityLiveData, Observer {
            if (it) fingerprintDialog.show() else fingerprintDialog.dismiss()
        })

        observe(viewModel.fingerPrintAutFailedLiveData, EventObserver {
            Toast.makeText(context, R.string.pincode_fingerprint_error, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.fingerPrintErrorLiveData, EventObserver {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        })

        observe(viewModel.pinCodeProgressLiveData, Observer {
            dotsProgressView.setProgress(it)
        })

        observe(viewModel.finisAppEvent, EventObserver {
            requireActivity().finish()
        })

        observe(viewModel.homeButtonVisibilityLiveData, Observer {
            if (it) {
                toolbar.showHomeButton()
            } else {
                toolbar.hideHomeButton()
            }
        })

        observe(viewModel.matchingPincodeErrorAnimationEvent, EventObserver {
            playMatchingPincodeErrorAnimation()
        })

        val action = arguments!!.getSerializable(PINCODE_ACTION_KEY) as PinCodeAction
        viewModel.startAuth(action)
    }

    private fun playMatchingPincodeErrorAnimation() {
        val animation = AnimationUtils.loadAnimation(activity!!, R.anim.shake)
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
            }

            override fun onAnimationEnd(animation: Animation?) {
            }
        })
        dotsProgressView.startAnimation(animation)
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onBackButtonPressed() {
        viewModel.backPressed()
    }
}