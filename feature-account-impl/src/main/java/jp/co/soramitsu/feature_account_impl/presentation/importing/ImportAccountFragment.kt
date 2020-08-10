package jp.co.soramitsu.feature_account_impl.presentation.importing

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
import jp.co.soramitsu.common.utils.EventObserver
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_import_account.advancedBlockView
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileEt
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileIcon
import kotlinx.android.synthetic.main.fragment_import_account.jsonFileInput
import kotlinx.android.synthetic.main.fragment_import_account.keyEt
import kotlinx.android.synthetic.main.fragment_import_account.keyInput
import kotlinx.android.synthetic.main.fragment_import_account.keyInputTitle
import kotlinx.android.synthetic.main.fragment_import_account.nextBtn
import kotlinx.android.synthetic.main.fragment_import_account.passwordEt
import kotlinx.android.synthetic.main.fragment_import_account.passwordInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeText
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import kotlinx.android.synthetic.main.fragment_import_account.usernameEt
import kotlinx.android.synthetic.main.fragment_import_account.usernameHintTv
import kotlinx.android.synthetic.main.fragment_import_account.usernameInput

class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {

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

        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }
        toolbar.setRightIconClickListener { viewModel.qrScanClicked() }

        sourceTypeInput.setOnClickListener { viewModel.sourceTypeInputClicked() }

        advancedBlockView.setOnEncryptionTypeClickListener {
            viewModel.encryptionTypeInputClicked()
        }

        advancedBlockView.setOnNetworkClickListener {
            viewModel.networkInputClicked()
        }

        nextBtn.setOnClickListener {
            viewModel.nextBtnClicked(keyEt.text.toString(), usernameEt.text.toString(), passwordEt.text.toString(), jsonFileEt.text.toString(), advancedBlockView.getDerivationPath())
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
        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .importAccountComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewModel) {
        observe(viewModel.sourceTypeChooserDialogInitialData, EventObserver {
            SourceTypeChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.sourceTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedSourceTypeLiveData, Observer {
            sourceTypeText.text = it.name
            keyInputTitle.text = it.name
            keyEt.setText("")
            jsonFileEt.setText("")
            passwordEt.setText("")
        })

        observe(viewModel.encryptionTypeChooserEvent, EventObserver {
            EncryptionTypeChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.encryptionTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedEncryptionTypeLiveData, Observer {
            advancedBlockView.setEncryption(it.name)
        })

        observe(viewModel.networkChooserEvent, EventObserver {
            NetworkChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.networkChanged(it)
            }.show()
        })

        observe(viewModel.selectedNetworkLiveData, Observer {
            advancedBlockView.setNetworkIconResource(it.icon)
            advancedBlockView.setNetworkName(it.name)
        })

        observe(viewModel.usernameVisibilityLiveData, Observer {
            if (it) {
                usernameInput.makeVisible()
                usernameHintTv.makeVisible()
            } else {
                usernameInput.makeGone()
                usernameHintTv.makeGone()
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