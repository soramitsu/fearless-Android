package jp.co.soramitsu.feature_account_impl.presentation.node.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.request.ImageRequest
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.common.utils.setDrawableStart
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
import kotlinx.coroutines.launch

class NodeDetailsFragment : BaseFragment<NodeDetailsViewModel>() {

    companion object {
        private const val CHAIN_ID_KEY = "chainId"
        private const val NODE_URL_KEY = "nodeUrl"

        fun getBundle(chainId: String, nodeUrl: String) = bundleOf(CHAIN_ID_KEY to chainId, NODE_URL_KEY to nodeUrl)
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
        val chainId = argument<String>(CHAIN_ID_KEY)
        val nodeUrl = argument<String>(NODE_URL_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .nodeDetailsComponentFactory()
            .create(this, chainId to nodeUrl)
            .inject(this)
    }

    override fun subscribe(viewModel: NodeDetailsViewModel) {
        viewModel.nodeModelLiveData.observe { node ->
            nodeDetailsNameField.content.setText(node.name)
            nodeDetailsHostField.content.setText(node.url)
        }

        viewModel.chainInfoLiveData.observe {
            nodeDetailsNetworkType.text = it.name

            val request = ImageRequest.Builder(requireContext())
                .size(resources.getDimension(R.dimen.chain_icon_size_small).toInt())
                .data(it.icon)
                .build()

            lifecycleScope.launch {
                val drawable = imageLoader.execute(request).drawable ?: return@launch
                nodeDetailsNetworkType.setDrawableStart(drawable)
            }
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
