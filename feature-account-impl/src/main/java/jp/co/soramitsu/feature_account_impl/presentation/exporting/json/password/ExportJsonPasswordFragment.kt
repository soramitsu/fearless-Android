package jp.co.soramitsu.feature_account_impl.presentation.exporting.json.password

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.utils.hideKeyboard
import jp.co.soramitsu.common.utils.setDrawableStart
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentExportJsonPasswordBinding
import jp.co.soramitsu.feature_account_impl.presentation.exporting.ExportFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import javax.inject.Inject

@AndroidEntryPoint
class ExportJsonPasswordFragment : ExportFragment<ExportJsonPasswordViewModel>() {

    @Inject
    protected lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentExportJsonPasswordBinding

    @Inject
    lateinit var factory: ExportJsonPasswordViewModel.ExportJsonPasswordViewModelFactory

    private val vm: ExportJsonPasswordViewModel by viewModels {
        ExportJsonPasswordViewModel.provideFactory(
            factory,
            argument(PAYLOAD_KEY)
        )
    }
    override val viewModel: ExportJsonPasswordViewModel
        get() = vm

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
