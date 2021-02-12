package jp.co.soramitsu.feature_account_impl.presentation.importing

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.FileRequester
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.JsonImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.MnemonicImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RawSeedImportSource
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.RequestCode
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.ImportSourceView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.JsonImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.MnemonicImportView
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.view.SeedImportView
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.AdvancedBlockView.FieldState
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.encryption.EncryptionTypeChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.NetworkChooserBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_import_account.advancedBlockView
import kotlinx.android.synthetic.main.fragment_import_account.nextBtn
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeContainer
import kotlinx.android.synthetic.main.fragment_import_account.sourceTypeInput
import kotlinx.android.synthetic.main.fragment_import_account.toolbar

class ImportAccountFragment : BaseFragment<ImportAccountViewModel>() {

    companion object {
        private const val KEY_FORCED_NETWORK_TYPE = "network_type"

        fun getBundle(networkType: Node.NetworkType?): Bundle {

            return Bundle().apply {
                putSerializable(KEY_FORCED_NETWORK_TYPE, networkType)
            }
        }
    }

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

        sourceTypeInput.setWholeClickListener { viewModel.openSourceChooserClicked() }

        advancedBlockView.setOnEncryptionTypeClickListener {
            viewModel.chooseEncryptionClicked()
        }

        nextBtn.setOnClickListener { viewModel.nextClicked() }

        nextBtn.prepareForProgress(viewLifecycleOwner)
    }

    override fun inject() {
        val forcedNetworkType = argument<Node.NetworkType?>(KEY_FORCED_NETWORK_TYPE)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .importAccountComponentFactory()
            .create(this, forcedNetworkType)
            .inject(this)
    }

    override fun subscribe(viewModel: ImportAccountViewModel) {
        sourceViews = viewModel.sourceTypes.map {
            val view = createSourceView(it)

            view.observeSource(it, viewLifecycleOwner)
            view.observeCommon(viewModel, viewLifecycleOwner)

            observeFeatures(it)

            view
        }

        viewModel.showSourceSelectorChooserLiveData.observeEvent(::showTypeChooser)

        viewModel.selectedSourceTypeLiveData.observe {
            val index = viewModel.sourceTypes.indexOf(it)

            sourceTypeContainer.removeAllViews()
            sourceTypeContainer.addView(sourceViews!![index])

            sourceTypeInput.setMessage(it.nameRes)
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

        viewModel.nextButtonState.observe(nextBtn::setState)

        viewModel.advancedBlockExceptNetworkEnabled.observe(::setSelectorsEnabled)

        advancedBlockView.derivationPathEditText.bindTo(viewModel.derivationPathLiveData, viewLifecycleOwner)
    }

    private fun observeFeatures(source: ImportSource) {
        if (source is FileRequester) {
            source.chooseJsonFileEvent.observeEvent {
                openFilePicker(it)
            }
        }
    }

    private fun setSelectorsEnabled(selectorsEnabled: Boolean) {
        val chooserState = getFieldState(selectorsEnabled)
        val derivationPathState = getFieldState(selectorsEnabled, disabledState = FieldState.HIDDEN)

        with(advancedBlockView) {
            configure(encryptionTypeField, chooserState)
            configure(derivationPathField, derivationPathState)
        }
    }

    private fun getFieldState(isEnabled: Boolean, disabledState: FieldState = FieldState.DISABLED): FieldState {
        return if (isEnabled) FieldState.NORMAL else disabledState
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        data?.let { viewModel.systemCallResultReceived(requestCode, it) }
    }

    private fun showTypeChooser(it: Payload<ImportSource>) {
        SourceTypeChooserBottomSheetDialog(requireActivity(), it, viewModel::sourceTypeChanged)
            .show()
    }

    private fun openFilePicker(it: RequestCode) {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/json"
        startActivityForResult(intent, it)
    }

    private fun createSourceView(source: ImportSource): ImportSourceView {
        val context = requireContext()

        return when (source) {
            is JsonImportSource -> JsonImportView(context)
            is MnemonicImportSource -> MnemonicImportView(context)
            is RawSeedImportSource -> SeedImportView(context)
        }
    }
}