package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.presentation.view.SoraProgressDialog
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.EventObserver
import jp.co.soramitsu.feature_main_api.di.MainFeatureApi
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.di.MainFeatureComponent
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeViewModel
import jp.co.soramitsu.feature_account_impl.presentation.pincode.fingerprint.FingerprintWrapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.BottomBarController
import jp.co.soramitsu.sora_ui.pinview.DotsProgressView
import kotlinx.android.synthetic.main.fragment_pincode.dotsProgressView
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeTitleTv
import kotlinx.android.synthetic.main.fragment_pincode.pinCodeView
import javax.inject.Inject

class PincodeFragment : BaseFragment<PinCodeViewModel>() {

    @Inject lateinit var fingerprintWrapper: FingerprintWrapper
    private lateinit var fingerprintDialog: BottomSheetDialog
    private lateinit var progressDialog: SoraProgressDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pincode, container, false)
    }

    override fun inject() {
        FeatureUtils.getFeature<MainFeatureComponent>(context!!, MainFeatureApi::class.java)
            .pinCodeComponentBuilder()
            .withFragment(this)
            .withMaxPinCodeLength(DotsProgressView.MAX_PROGRESS)
            .build()
            .inject(this)
    }

    override fun initViews() {
        (activity as BottomBarController).hideBottomBar()

        progressDialog = SoraProgressDialog(activity!!)

        fingerprintDialog = BottomSheetDialog(activity!!).apply {
            setContentView(R.layout.fingerprint_bottom_dialog)
            setCancelable(true)
            setOnCancelListener { fingerprintWrapper.cancel() }
            findViewById<TextView>(R.id.btnCancel)?.setOnClickListener { fingerprintWrapper.cancel() }
        }

        with(pinCodeView) {
            pinCodeListener = { viewModel.pinCodeNumberClicked(it) }
            deleteClickListener = { viewModel.pinCodeDeleteClicked() }
            fingerprintClickListener = { fingerprintWrapper.toggleScanner() }
        }
    }

    override fun subscribe(viewModel: PinCodeViewModel) {
        observe(viewModel.getProgressVisibility(), Observer {
            if (it) progressDialog.show() else progressDialog.dismiss()
        })

        observe(viewModel.startFingerprintScannerEventLiveData, EventObserver {
            if (fingerprintWrapper.isAuthReady()) {
                fingerprintWrapper.startAuth()
            }
        })

        observe(viewModel.showFingerPrintEventLiveData, EventObserver {
            pinCodeView.changeFingerPrintButtonVisibility(fingerprintWrapper.isAuthReady())
        })

        observe(viewModel.toolbarTitleResLiveData, Observer {
            pinCodeTitleTv.setText(it)
        })

        observe(viewModel.wrongPinCodeEventLiveData, EventObserver {
            Toast.makeText(activity, getString(R.string.common_error_pincode_check_error), Toast.LENGTH_LONG).show()
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
            (activity as MainActivity).closeApp()
        })

        observe(viewModel.checkInviteLiveData, EventObserver {
            (activity as MainActivity).checkInviteAction()
        })

        observe(viewModel.ethServiceEvent, EventObserver {
            (activity as MainActivity).startEthService()
        })

        val action = arguments!!.getSerializable(Const.PIN_CODE_ACTION) as PinCodeAction
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