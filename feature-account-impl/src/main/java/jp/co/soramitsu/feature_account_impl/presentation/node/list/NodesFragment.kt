package jp.co.soramitsu.feature_account_impl.presentation.node.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.node.list.accounts.AccountChooserBottomSheetDialog
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import kotlinx.android.synthetic.main.fragment_nodes.addConnectionTv
import kotlinx.android.synthetic.main.fragment_nodes.connectionsList
import kotlinx.android.synthetic.main.fragment_nodes.fearlessToolbar

class NodesFragment : BaseFragment<NodesViewModel>(), NodesAdapter.NodeItemHandler {

    private lateinit var adapter: NodesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_nodes, container, false)

    override fun initViews() {
        adapter = NodesAdapter(this)

        connectionsList.setHasFixedSize(true)
        connectionsList.adapter = adapter

        fearlessToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }

        fearlessToolbar.setRightActionClickListener {
            viewModel.editClicked()
        }

        addConnectionTv.setOnClickListener {
            viewModel.addNodeClicked()
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .connectionsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: NodesViewModel) {
        viewModel.groupedNodeModelsLiveData.observe(adapter::submitList)

        viewModel.selectedNodeLiveData.observe(adapter::updateSelectedNode)

        viewModel.noAccountsEvent.observeEvent {
            showNoAccountsDialog(it)
        }

        viewModel.showAccountChooserLiveData.observeEvent {
            AccountChooserBottomSheetDialog(requireActivity(), it, viewModel::accountSelected).show()
        }

        viewModel.editMode.observe(adapter::switchToEdit)

        viewModel.toolbarAction.observe(fearlessToolbar::setTextRight)

        viewModel.deleteNodeEvent.observeEvent(::showDeleteNodeDialog)
    }

    override fun infoClicked(nodeModel: NodeModel, isChecked: Boolean) {
        viewModel.infoClicked(nodeModel, isChecked)
    }

    override fun checkClicked(nodeModel: NodeModel) {
        viewModel.selectNodeClicked(nodeModel)
    }

    override fun deleteClicked(nodeModel: NodeModel) {
        viewModel.deleteNodeClicked(nodeModel)
    }

    private fun showDeleteNodeDialog(nodeModel: NodeModel) {
        val message = getString(
            R.string.connection_delete_description_v1_0_1,
            nodeModel.networkModelType.networkType.readableName,
            nodeModel.name
        )

        MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.connection_delete_title)
            .setMessage(message)
            .setPositiveButton(R.string.connection_delete_confirm) { dialog, _ ->
                viewModel.confirmNodeDeletion(nodeModel)
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog?.dismiss() }
            .show()
    }

    private fun showNoAccountsDialog(networkType: Node.NetworkType) {
        MaterialAlertDialogBuilder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.account_needed_title)
            .setMessage(R.string.account_needed_message)
            .setPositiveButton(R.string.common_proceed) { dialog, _ ->
                viewModel.createAccountForNetworkType(networkType)
                dialog?.dismiss()
            }
            .setNegativeButton(R.string.common_cancel) { dialog, _ -> dialog?.dismiss() }
            .show()
    }
}