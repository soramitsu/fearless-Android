package jp.co.soramitsu.feature_account_impl.presentation.node.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import kotlinx.android.synthetic.main.fragment_nodes.addNodeButton
import kotlinx.android.synthetic.main.fragment_nodes.autoSelectNodesLabel
import kotlinx.android.synthetic.main.fragment_nodes.autoSelectNodesSwitch
import kotlinx.android.synthetic.main.fragment_nodes.connectionsList
import kotlinx.android.synthetic.main.fragment_nodes.nodesToolbar
import javax.inject.Inject

class NodesFragment : BaseFragment<NodesViewModel>(), NodesAdapter.NodeItemHandler {

    private lateinit var adapter: NodesAdapter

    companion object {
        private const val CHAIN_ID_KEY = "chainIdKey"

        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_nodes, container, false)

    override fun initViews() {
        adapter = NodesAdapter(this)

        connectionsList.setHasFixedSize(true)
        connectionsList.adapter = adapter

        nodesToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        nodesToolbar.setRightActionClickListener {
            viewModel.editClicked()
        }

        addNodeButton.setOnClickListener {
            viewModel.addNodeClicked()
        }

        autoSelectNodesSwitch.bindTo(viewModel.autoSelectedNodeFlow, lifecycleScope)
    }

    override fun inject() {
        val chainId = argument<String>(CHAIN_ID_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .connectionsComponentFactory()
            .create(this, chainId)
            .inject(this)
    }

    override fun subscribe(viewModel: NodesViewModel) {
        viewModel.groupedNodeModelsLiveData.observe(adapter::submitList)

        viewModel.editMode.observe(adapter::switchToEdit)

        viewModel.toolbarAction.observe(nodesToolbar.rightActionText::setText)

        viewModel.deleteNodeEvent.observeEvent(::showDeleteNodeDialog)

        viewModel.chainName.observe(nodesToolbar::setTitle)

        viewModel.autoSelectedNodeFlow.observe {
            autoSelectNodesLabel.isEnabled = it
            adapter.handleAutoSelected(it)
        }

        viewModel.hasCustomNodeModelsLiveData.observe(nodesToolbar.rightActionText::setEnabled)
    }

    override fun infoClicked(nodeModel: NodeModel) {
        viewModel.infoClicked(nodeModel)
    }

    override fun checkClicked(nodeModel: NodeModel) {
        viewModel.selectNodeClicked(nodeModel)
    }

    override fun deleteClicked(nodeModel: NodeModel) {
        viewModel.deleteNodeClicked(nodeModel)
    }

    private fun showDeleteNodeDialog(nodeModel: NodeModel) {
        MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.delete_custom_node_title)
            .setMessage(nodeModel.name)
            .setPositiveButton(R.string.connection_delete_confirm) { dialog, _ ->
                viewModel.confirmNodeDeletion(nodeModel)
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog?.dismiss() }
            .show()
    }
}
