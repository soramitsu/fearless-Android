package jp.co.soramitsu.feature_account_impl.presentation.node.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
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
            nodeDetailsNameField.content.setText(node.name)
            nodeDetailsHostField.content.setText(node.link)
        }

        viewModel.editEnabled.observe { editEnabled ->
            updateBtn.setVisible(editEnabled)

            nodeDetailsNameField.content.isEnabled = editEnabled
            nodeDetailsHostField.content.isEnabled = editEnabled

            nodeDetailsNameField.content.onTextChanged {
                viewModel.nodeDetailsEdited()
            }

            nodeDetailsHostField.content.onTextChanged {
                viewModel.nodeDetailsEdited()
            }
        }

        viewModel.updateButtonEnabled.observe {
            updateBtn.isEnabled = it
        }
    }
}