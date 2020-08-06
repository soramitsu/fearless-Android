package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
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
import kotlinx.android.synthetic.main.fragment_create_account.toolbar
import kotlinx.android.synthetic.main.fragment_import_account.advanced
import kotlinx.android.synthetic.main.fragment_import_account.derivationPathEt
import kotlinx.android.synthetic.main.fragment_import_account.derivationPathInput
import kotlinx.android.synthetic.main.fragment_import_account.encryptionTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.encryptionTypeText
import kotlinx.android.synthetic.main.fragment_import_account.keyInputTitle
import kotlinx.android.synthetic.main.fragment_import_account.networkInput
import kotlinx.android.synthetic.main.fragment_import_account.networkText
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeText


class ImportAccountFragment : BaseFragment<ImportAccountViewmodel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_import_account, container, false)
    }

    override fun initViews() {
        toolbar.showRightButton()
        toolbar.setRightIconButtonListener {  }
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        advanced.setOnClickListener { viewModel.advancedButtonClicked() }
        sourceTypeInput.setOnClickListener { viewModel.sourceTypeInputClicked() }
        encryptionTypeInput.setOnClickListener { viewModel.encryptionTypeInputClicked() }
        networkInput.setOnClickListener { viewModel.networkTypeInputClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
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
                activity!!,
                it
            ) {
                viewModel.sourceTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedSourceTypeText, Observer {
            sourceTypeText.text = it
            keyInputTitle.text = it
        })

        observe(viewModel.encryptionTypeChooserDialogInitialData, Observer {
            EncryptionTypeChooserBottomSheetDialog(
                activity!!,
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
                activity!!,
                it
            ) {
                viewModel.networkTypeChanged(it)
            }.show()
        })

        observe(viewModel.selectedNodeText, Observer {
            networkText.text = it
        })
    }
}