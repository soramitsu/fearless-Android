package jp.co.soramitsu.feature_account_impl.presentation.node.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodesBinding
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import javax.inject.Inject

@AndroidEntryPoint
class NodesFragment : BaseFragment<NodesViewModel>(), NodesAdapter.NodeItemHandler {

    private lateinit var adapter: NodesAdapter

    companion object {
        private const val CHAIN_ID_KEY = "chainIdKey"

        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    @Inject
    lateinit var imageLoader: ImageLoader

    private lateinit var binding: FragmentNodesBinding

    @Inject
    lateinit var factory: NodesViewModel.NodesViewModelFactory

    private val vm: NodesViewModel by viewModels {
        NodesViewModel.provideFactory(
            factory,
            argument(CHAIN_ID_KEY)
        )
    }
    override val viewModel: NodesViewModel
        get() = vm

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
        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
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
