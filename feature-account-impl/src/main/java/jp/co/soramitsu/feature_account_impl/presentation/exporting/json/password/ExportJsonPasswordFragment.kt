package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import coil.ImageLoader
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.setDrawableStart
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportJsonPasswordBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import javax.inject.Inject

class ExportJsonPasswordFragment : ExportFragment<ExportJsonPasswordViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentExportJsonPasswordBinding

    companion object {
        private const val PAYLOAD_KEY = "PAYLOAD_KEY"

        fun getBundle(metaId: Long, chainId: ChainId, isExportWallet: Boolean) =
            bundleOf(PAYLOAD_KEY to ExportJsonPasswordPayload(metaId, chainId, isExportWallet))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExportJsonPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.resetProgress()
    }

    override fun initViews() {
        with(binding) {
            exportJsonPasswordToolbar.setHomeButtonListener {
                hideKeyboard()
                viewModel.back()
            }

            exportJsonPasswordToolbar.setTitle(
                when {
                    viewModel.isExportWallet -> R.string.export_wallet
                    else -> R.string.account_export
                }
            )

            exportJsonPasswordNext.prepareForProgress(viewLifecycleOwner)
            exportJsonPasswordNext.setOnClickListener { viewModel.nextClicked() }

            exportJsonPasswordMatchingError.setDrawableStart(R.drawable.ic_red_cross, 24)

            exportJsonPasswordNetworkInput.isEnabled = false
            exportJsonPasswordNetworkInput.isVisible = viewModel.isExportWallet.not()
        }
    }

    override fun inject() {
        val payload = argument<ExportJsonPasswordPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(requireContext(), AccountFeatureApi::class.java)
            .exportJsonPasswordFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: ExportJsonPasswordViewModel) {
        super.subscribe(viewModel)
        binding.exportJsonPasswordNewField.content.bindTo(viewModel.passwordLiveData)
        binding.exportJsonPasswordConfirmField.content.bindTo(viewModel.passwordConfirmationLiveData)

        viewModel.nextButtonState.observe(binding.exportJsonPasswordNext::setState)

        viewModel.showDoNotMatchingErrorLiveData.observe {
            binding.exportJsonPasswordMatchingError.setVisible(it, falseState = View.INVISIBLE)
        }

        viewModel.chainLiveData.observe {
            binding.exportJsonPasswordNetworkInput.loadIcon(it.icon, imageLoader)
            binding.exportJsonPasswordNetworkInput.setMessage(it.name)
        }
    }
}
