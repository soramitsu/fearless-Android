package jp.co.soramitsu.account.impl.presentation.node.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.soramitsu.account.impl.presentation.node.model.NodeModel
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.ErrorDialog
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodesBinding

@AndroidEntryPoint
class NodesFragment : BaseFragment<NodesViewModel>(), NodesAdapter.NodeItemHandler {

    private lateinit var adapter: NodesAdapter

    companion object {
        const val CHAIN_ID_KEY = "chainIdKey"

        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentNodesBinding

    override val viewModel: NodesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNodesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initViews() {
        adapter = NodesAdapter(this)

        with(binding) {
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
    }

    override fun subscribe(viewModel: NodesViewModel) {
        viewModel.groupedNodeModelsLiveData(argument(CHAIN_ID_KEY)).observe(adapter::submitList)

        viewModel.editMode.observe(adapter::switchToEdit)

        viewModel.toolbarAction.observe(binding.nodesToolbar.rightActionText::setText)

        viewModel.deleteNodeEvent.observeEvent(::showDeleteNodeDialog)

        viewModel.chainName.observe(binding.nodesToolbar::setTitle)

        viewModel.autoSelectedNodeFlow.observe {
            binding.autoSelectNodesLabel.isEnabled = it
            adapter.handleAutoSelected(it)
        }

        viewModel.hasCustomNodeModelsLiveData.observe(binding.nodesToolbar.rightActionText::setEnabled)
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
        val res = requireContext()
        ErrorDialog(
            title = res.getString(R.string.delete_custom_node_title),
            message = nodeModel.name,
            positiveButtonText = res.getString(R.string.common_delete),
            negativeButtonText = res.getString(R.string.common_cancel),
            positiveClick = { viewModel.confirmNodeDeletion(nodeModel) }
        ).show(childFragmentManager)
    }
}
