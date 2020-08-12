package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.view.FearlessProgressDialog
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.feature_account_impl.presentation.pincode.view.DotsProgressView
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import kotlinx.android.synthetic.main.fragment_pincode.dotsProgressView
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeTitleTv
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeView
import javax.inject.Inject

class PincodeFragment : BaseFragment<PinCodeViewModel>() {

    @Inject lateinit var fingerprintWrapper: FingerprintWrapper
    private lateinit var fingerprintDialog: BottomSheetDialog
    private lateinit var progressDialog: FearlessProgressDialog

    companion object {
        const val PINCODE_ACTION_KEY = "pincode_action"

        fun getBundle(action: PinCodeAction): Bundle {
            return Bundle().apply {
                putSerializable(PINCODE_ACTION_KEY, action)
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

        progressDialog = FearlessProgressDialog(activity!!)

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
            AlertDialog.Builder(requireActivity())
                .setTitle(R.string.pincode_fingerprint_switch_dialog_title)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    viewModel.fingerprintSwithcDialogYesClicked()
                }
                .setNegativeButton(android.R.string.no) { _, _ ->
                    viewModel.fingerprintSwithcDialogNoClicked()
                }
                .show()
        })

        observe(viewModel.showFingerPrintEventLiveData, EventObserver {
            pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady())
        })

        observe(viewModel.toolbarTitleResLiveData, Observer {
            pinCodeTitleTv.setText(it)
        })

        observe(viewModel.wrongPinCodeEventLiveData, EventObserver {
            Toast.makeText(activity, getString(R.string.pincode_check_error), Toast.LENGTH_LONG).show()
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

        observe(viewModel.deleteButtonVisibilityLiveData, Observer {
            pinCodeView.changeDeleteButtonVisibility(it)
        })

        observe(viewModel.closeAppLiveData, EventObserver {
            requireActivity().finish()
        })

        val action = arguments!!.getSerializable(PINCODE_ACTION_KEY) as PinCodeAction
        viewModel.startAuth(action)
    }

    fun onBackPressed() {
        viewModel.backPressed()
    }

    override fun onPause() {
        super.onPause()
        fingerprintWrapper.cancel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}