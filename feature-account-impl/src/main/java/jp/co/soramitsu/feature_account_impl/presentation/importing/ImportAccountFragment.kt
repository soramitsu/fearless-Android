package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.ImportSourceView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.JsonImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.MnemonicImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.SeedImportView
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_import_account.advancedBlockView
import kotlinx.android.synthetic.main.fragment_import_account.nextBtn
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeContainer
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeText
import kotlinx.android.synthetic.main.fragment_import_account.toolbar
import javax.inject.Inject

class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {

    companion object {
        private const val PICK_FILE_RESULT_CODE = 101
        private const val KEY_NETWORK_TYPE = "network_type"

        fun getBundle(networkType: Node.NetworkType?): Bundle {

            return Bundle().apply {
                putSerializable(KEY_NETWORK_TYPE, networkType)
            }
        }
    }

    @Inject
    lateinit var externalFileReader: FileReader

    private var sourceViews: List<View>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_import_account, container, false)
    }

    override fun initViews() {
        toolbar.setHomeButtonListener { viewModel.homeButtonClicked() }

        sourceTypeInput.setOnClickListener { viewModel.openSourceChooserClicked() }

        advancedBlockView.setOnEncryptionTypeClickListener {
            viewModel.chooseEncryptionClicked()
        }

        advancedBlockView.setOnNetworkClickListener {
            viewModel.chooseNetworkClicked()
        }

        nextBtn.setOnClickListener { viewModel.nextClicked() }
    }

    override fun inject() {
        val networkType = argument<Node.NetworkType?>(KEY_NETWORK_TYPE)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .importAccountComponentFactory()
            .create(this, networkType)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewModel) {
        advancedBlockView.setNetworkSelectorEnabled(viewModel.isNetworkTypeChangeAvailable)

        sourceViews = viewModel.sourceTypes.map {
            val view = createSourceView(it)

            view.observeSource(it, viewLifecycleOwner)
            view.observeCommon(viewModel, viewLifecycleOwner)

            view
        }

        viewModel.showSourceChooserLiveData.observeEvent {
            SourceTypeChooserBottomSheetDialog(requireActivity(), it) {
                viewModel.sourceTypeChanged(it)
            }.show()
        }

        viewModel.selectedSourceTypeLiveData.observe {
            val index = viewModel.sourceTypes.indexOf(it)

            sourceTypeContainer.removeAllViews()
            sourceTypeContainer.addView(sourceViews!![index])

            sourceTypeText.setText(it.nameRes)

            val isSelectorsEnabled = it !is JsonImportSource

            advancedBlockView.setSelectorsEnabled(isSelectorsEnabled)
        }

        viewModel.encryptionTypeChooserEvent.observeEvent {
            EncryptionTypeChooserBottomSheetDialog(
                requireActivity(),
                it,
                viewModel.selectedEncryptionTypeLiveData::setValue
            )
                .show()
        }

        viewModel.selectedEncryptionTypeLiveData.observe {
            advancedBlockView.setEncryption(it.name)
        }

        viewModel.networkChooserEvent.observeEvent {
            NetworkChooserBottomSheetDialog(
                requireActivity(),
                it,
                viewModel.selectedNetworkLiveData::setValue
            ).show()
        }

        viewModel.selectedNetworkLiveData.observe {
            advancedBlockView.setNetworkIconResource(it.networkTypeUI.icon)
            advancedBlockView.setNetworkName(it.name)
        }

        viewModel.nextButtonEnabledLiveData.observe {
            nextBtn.isEnabled = it
        }

        advancedBlockView.derivationPathField.bindTo(viewModel.derivationPathLiveData, viewLifecycleOwner)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.let {
            if (requestCode == PICK_FILE_RESULT_CODE) {
                processJsonOpenIntent(it)
            }
        }
    }

    private fun processJsonOpenIntent(intent: Intent) {
        val fileContent = externalFileReader.readFile(intent.data!!)

        viewModel.fileChosen(fileContent)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        startActivityForResult(intent, PICK_FILE_RESULT_CODE)
    }

    private fun createSourceView(source: ImportSource): ImportSourceView {
        val context = requireContext()

        return when (source) {
            is JsonImportSource -> {
                val view = JsonImportView(context)

                view.setImportFromFileClickListener(::openFilePicker)

                view
            }
            is MnemonicImportSource -> MnemonicImportView(context)
            is RawSeedImportSource -> SeedImportView(context)
        }
    }
}