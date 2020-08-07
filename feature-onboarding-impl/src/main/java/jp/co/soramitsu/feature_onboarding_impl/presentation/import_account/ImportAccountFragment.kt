package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.google.zxing.integration.android.IntentIntegrator
import com.tbruyelle.rxpermissions2.RxPermissions
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.NetworkTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.SourceTypeChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_import_account.advanced
import kotlinx.android.synthetic.main.fragment_import_account.derivationPathEt
import kotlinx.android.synthetic.main.fragment_import_account.derivationPathInput
import kotlinx.android.synthetic.main.fragment_import_account.encryptionTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.encryptionTypeText
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileEt
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileIcon
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileInput
import kotlinx.android.synthetic.main.fragment_import_account.keyEt
import kotlinx.android.synthetic.main.fragment_import_account.keyInput
import kotlinx.android.synthetic.main.fragment_import_account.keyInputTitle
import kotlinx.android.synthetic.main.fragment_import_account.networkInput
import kotlinx.android.synthetic.main.fragment_import_account.networkText
import kotlinx.android.synthetic.main.fragment_import_account.nextBtn
import kotlinx.android.synthetic.main.fragment_import_account.passwordEt
import kotlinx.android.synthetic.main.fragment_import_account.passwordInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeText
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import kotlinx.android.synthetic.main.fragment_import_account.usernameEt
import kotlinx.android.synthetic.main.fragment_import_account.usernameInput

class ImportAccountFragment : BaseFragment<ImportAccountViewmodel>() {

    companion object {
        private const val PICKFILE_RESULT_CODE = 101
    }

    private lateinit var integrator: IntentIntegrator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import_account, container, false)
    }

    override fun initViews() {
        integrator = IntentIntegrator.forSupportFragment(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES)
            setPrompt("")
            setBeepEnabled(false)
            setOrientationLocked(true)
        }

        toolbar.showRightButton()
        toolbar.setRightIconButtonListener { }
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
        toolbar.setRightIconButtonListener { viewModel.qrScanClicked() }

        advanced.setOnClickListener { viewModel.advancedButtonClicked() }
        sourceTypeInput.setOnClickListener { viewModel.sourceTypeInputClicked() }
        encryptionTypeInput.setOnClickListener { viewModel.encryptionTypeInputClicked() }
        networkInput.setOnClickListener { viewModel.networkTypeInputClicked() }
        nextBtn.setOnClickListener {
            viewModel.nextBtnClicked(keyEt.text.toString(), usernameEt.text.toString(), passwordEt.text.toString(), jsonFileEt.text.toString(), derivationPathEt.text.toString())
        }

        jsonFileIcon.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/json"
            startActivityForResult(intent, PICKFILE_RESULT_CODE)
        }

        usernameEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.inputChanges(s.toString(), keyEt.text.toString())
            }
        })

        keyEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.inputChanges(usernameEt.text.toString(), s.toString())
            }
        })

        jsonFileEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.inputChanges(s.toString(), passwordEt.text.toString())
            }
        })

        passwordEt.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.inputChanges(jsonFileEt.text.toString(), s.toString())
            }
        })
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(requireContext(), OnboardingFeatureApi::class.java)
            .importAccountComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewmodel) {
        observe(viewModel.advancedVisibilityLiveData, Observer {
            if (it) {
                encryptionTypeInput.makeVisible()
                derivationPathInput.makeVisible()
                networkInput.makeVisible()
                advanced.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_minus, 0)
            } else {
                encryptionTypeInput.makeGone()
                derivationPathInput.makeGone()
                networkInput.makeGone()
                advanced.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_plus, 0)
            }
        })

        observe(viewModel.sourceTypeChooserDialogInitialData, Observer {
            SourceTypeChooserBottomSheetDialog(
                requireActivity(),
                it
            ) {
                viewModel.sourceTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedSourceTypeText, Observer {
            sourceTypeText.text = it
            keyInputTitle.text = it
            keyEt.setText("")
            jsonFileEt.setText("")
            passwordEt.setText("")
        })

        observe(viewModel.encryptionTypeChooserDialogInitialData, Observer {
            EncryptionTypeChooserBottomSheetDialog(
                requireActivity(),
                it
            ) {
                viewModel.encryptionTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedEncryptionTypeText, Observer {
            encryptionTypeText.text = it
            derivationPathEt.setText("")
        })

        observe(viewModel.networkTypeChooserDialogInitialData, Observer {
            NetworkTypeChooserBottomSheetDialog(
                requireActivity(),
                it
            ) {
                viewModel.networkTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedNodeText, Observer {
            networkText.text = it
        })

        observe(viewModel.usernameVisibilityLiveData, Observer {
            if (it) {
                usernameInput.makeVisible()
            } else {
                usernameInput.makeGone()
            }
        })

        observe(viewModel.passwordVisibilityLiveData, Observer {
            if (it) {
                passwordInput.makeVisible()
            } else {
                passwordInput.makeGone()
            }
        })

        observe(viewModel.jsonInputVisibilityLiveData, Observer {
            if (it) {
                jsonFileInput.makeVisible()
                keyInput.makeGone()
            } else {
                jsonFileInput.makeGone()
                keyInput.makeVisible()
            }
        })

        observe(viewModel.qrScanStartLiveData, Observer {
            RxPermissions(this@ImportAccountFragment)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it) integrator.initiateScan()
                }
        })

        observe(viewModel.nextButtonEnabledLiveData, Observer {
            nextBtn.isEnabled = it
        })

        observe(viewModel.selectedNodeIcon, Observer {
            networkText.setCompoundDrawablesWithIntrinsicBounds(it, 0, 0, 0)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.let {
            if (requestCode == PICKFILE_RESULT_CODE) {
                processJsonOpenIntent(it)
            }
        }
    }

    private fun processJsonOpenIntent(intent: Intent) {
        val file = requireActivity().contentResolver.openInputStream(intent.data!!)
        file?.reader(Charsets.UTF_8)?.readText()?.let {
            if (it.length < 1000) {
                jsonFileEt.setText(it)
            }
        }
    }
}