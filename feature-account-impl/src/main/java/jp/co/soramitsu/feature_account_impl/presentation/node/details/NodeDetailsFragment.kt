package jp.co.soramitsu.feature_account_impl.presentation.node.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.onTextChanged
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_node_details.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsHost
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsHostContainer
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsName
import kotlinx.android.synthetic.main.fragment_node_details.nodeDetailsNameContainer
import kotlinx.android.synthetic.main.fragment_node_details.nodeHostCopy
import kotlinx.android.synthetic.main.fragment_node_details.updateBtn

class NodeDetailsFragment : BaseFragment<NodeDetailsViewModel>() {

    companion object {
        private const val KEY_NODE_ID = "node_id"

        fun getBundle(nodeId: Int): Bundle {
            return Bundle().apply {
                putInt(KEY_NODE_ID, nodeId)
            }
        }
    }

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
    }

    override fun inject() {
        val nodeId = arguments!!.getInt(KEY_NODE_ID)
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .nodeDetailsComponentFactory()
            .create(this, nodeId)
            .inject(this)
    }

    override fun subscribe(viewModel: NodeDetailsViewModel) {
        viewModel.nodeLiveData.observe { node ->
            nodeDetailsName.setText(node.name)
            nodeDetailsHost.setText(node.link)
        }

        viewModel.editEnabled.observe { editEnabled ->
            updateBtn.visibility = if (editEnabled) View.VISIBLE else View.GONE

            nodeDetailsName.isEnabled = editEnabled
            nodeDetailsHost.isEnabled = editEnabled

            if (editEnabled) {
                nodeDetailsHost.setBackgroundResource(R.drawable.bg_input_shape_selector)
                nodeDetailsNameContainer.setBackgroundResource(R.drawable.bg_input_shape_selector)
            } else {
                nodeDetailsHostContainer.setBackgroundResource(R.drawable.bg_button_primary_disabled)
                nodeDetailsNameContainer.setBackgroundResource(R.drawable.bg_button_primary_disabled)
            }

            nodeDetailsName.onTextChanged {
                viewModel.nodeDetailsEdited()
            }

            nodeDetailsHost.onTextChanged {
                viewModel.nodeDetailsEdited()
            }
        }

        viewModel.updateButtonEnabled.observe {
            updateBtn.isEnabled = it
        }
    }
}