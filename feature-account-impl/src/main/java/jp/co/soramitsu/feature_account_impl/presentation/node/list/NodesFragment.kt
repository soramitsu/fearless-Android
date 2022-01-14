package jp.co.soramitsu.feature_account_impl.presentation.node.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import coil.ImageLoader
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.node.model.NodeModel
import kotlinx.android.synthetic.main.fragment_nodes.addConnectionTv
import kotlinx.android.synthetic.main.fragment_nodes.backButton
import kotlinx.android.synthetic.main.fragment_nodes.connectionsList
import kotlinx.android.synthetic.main.fragment_nodes.rightText
import kotlinx.android.synthetic.main.fragment_nodes.titleTextView
import kotlinx.android.synthetic.main.fragment_nodes.tokenIcon

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

        backButton.setOnClickListener {
            viewModel.backClicked()
        }

        rightText.setOnClickListener {
            viewModel.editClicked()
        }

        addConnectionTv.setOnClickListener {
            viewModel.addNodeClicked()
        }
    }

    override fun inject() {
        val chainId = arguments?.getString(CHAIN_ID_KEY)!!

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

        viewModel.toolbarAction.observe(rightText::setText)

        viewModel.deleteNodeEvent.observeEvent(::showDeleteNodeDialog)

        viewModel.chainInfo.observe {
            val (name, icon) = it
            tokenIcon.load(icon, imageLoader)
            titleTextView.text = getString(R.string.connection_management_title, name)
        }
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
        val message = getString(
            R.string.connection_delete_description,
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
}
