package jp.co.soramitsu.feature_account_impl.presentation.node.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_node_details.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsHostField
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsNameField
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsNetworkType
import kotlinx.android.synthetic.main.fragment_node_details.nodeHostCopy
import kotlinx.android.synthetic.main.fragment_node_details.updateBtn
import javax.inject.Inject

class NodeDetailsFragment : BaseFragment<NodeDetailsViewModel>() {

    companion object {
        private const val PAYLOAD_KEY = "payload"

        fun getBundle(payload: NodeDetailsPayload) = bundleOf(PAYLOAD_KEY to payload)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_node_details, container, false)

    override fun initViews() {
        fearlessToolbar.setHomeButtonListener { viewModel.backClicked() }

        nodeHostCopy.setOnClickListener {
            viewModel.copyNodeHostClicked()
        }

        updateBtn.setOnClickListener {
            viewModel.updateClicked(nodeDetailsNameField.content.text.toString(), nodeDetailsHostField.content.text.toString())
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
            nodeDetailsNameField.content.setText(node.name)
            nodeDetailsHostField.content.setText(node.url)
        }

        viewModel.chainInfoLiveData.observe {
            nodeDetailsNetworkType.setMessage(it.name)
            nodeDetailsNetworkType.loadIcon(it.icon, imageLoader)
        }

        viewModel.nameEditEnabled.observe { editEnabled ->
            updateBtn.setVisible(editEnabled)

            nodeDetailsNameField.content.isEnabled = editEnabled

            nodeDetailsNameField.content.onTextChanged {
                viewModel.nodeDetailsEdited()
            }
        }

        viewModel.hostEditEnabled.observe { editEnabled ->
            nodeDetailsHostField.content.isEnabled = editEnabled

            nodeDetailsHostField.content.onTextChanged {
                viewModel.nodeDetailsEdited()
            }
        }

        viewModel.updateButtonEnabled.observe {
            updateBtn.isEnabled = it
        }
    }
}
