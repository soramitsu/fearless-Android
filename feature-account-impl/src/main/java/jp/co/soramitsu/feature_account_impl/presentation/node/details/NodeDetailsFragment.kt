package jp.co.soramitsu.feature_account_impl.presentation.node.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodeDetailsBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import javax.inject.Inject

class NodeDetailsFragment : BaseFragment<NodeDetailsViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "payload"

        fun getBundle(payload: NodeDetailsPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentNodeDetailsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) : View {
        binding = FragmentNodeDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            fearlessToolbar.setHomeButtonListener { viewModel.backClicked() }

            nodeHostCopy.setOnClickListener {
                viewModel.copyNodeHostClicked()
            }

            updateBtn.setOnClickListener {
                viewModel.updateClicked(nodeDetailsNameField.content.text.toString(), nodeDetailsHostField.content.text.toString())
            }
        }
    }

    override fun inject() {
        val payload = argument<NodeDetailsPayload>(PAYLOAD_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .nodeDetailsComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: NodeDetailsViewModel) {
        viewModel.nodeModelLiveData.observe { node ->
            binding.nodeDetailsNameField.content.setText(node.name)
            binding.nodeDetailsHostField.content.setText(node.url)
        }

        viewModel.chainInfoLiveData.observe {
            binding.nodeDetailsNetworkType.setMessage(it.name)
            binding.nodeDetailsNetworkType.loadIcon(it.icon, imageLoader)
        }

        viewModel.nameEditEnabled.observe { editEnabled ->
            with(binding) {
                updateBtn.setVisible(editEnabled)

                nodeDetailsNameField.content.isEnabled = editEnabled

                nodeDetailsNameField.content.onTextChanged {
                    viewModel.nodeDetailsEdited()
                }
            }
        }

        viewModel.hostEditEnabled.observe { editEnabled ->
            binding.nodeDetailsHostField.content.isEnabled = editEnabled

            binding.nodeDetailsHostField.content.onTextChanged {
                viewModel.nodeDetailsEdited()
            }
        }

        viewModel.updateButtonEnabled.observe {
            binding.updateBtn.isEnabled = it
        }
    }
}
