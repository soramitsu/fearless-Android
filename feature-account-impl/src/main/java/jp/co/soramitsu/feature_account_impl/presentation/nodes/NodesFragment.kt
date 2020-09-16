package jp.co.soramitsu.feature_account_impl.presentation.nodes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import jp.co.soramitsu.feature_account_impl.presentation.nodes.model.NodeModel
import jp.co.soramitsu.feature_account_impl.presentation.view.advanced.network.model.NetworkModel
import kotlinx.android.synthetic.main.fragment_accounts.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_nodes.connectionsList

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
    }

    override fun infoClicked(nodeModel: NodeModel) {
        viewModel.infoClicked(nodeModel)
    }

    override fun checkClicked(nodeModel: NodeModel) {
        viewModel.selectNetworkClicked(nodeModel)
    }
}