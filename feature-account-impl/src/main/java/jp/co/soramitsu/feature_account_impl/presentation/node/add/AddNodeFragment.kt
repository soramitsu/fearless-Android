package jp.co.soramitsu.feature_account_impl.presentation.node.add

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent
import kotlinx.android.synthetic.main.fragment_node_add.addBtn
import kotlinx.android.synthetic.main.fragment_node_add.fearlessToolbar
import kotlinx.android.synthetic.main.fragment_node_add.nodeHostField
import kotlinx.android.synthetic.main.fragment_node_add.nodeNameField

class AddNodeFragment : BaseFragment<AddNodeViewModel>() {

    companion object {
        private const val CHAIN_ID_KEY = "chainIdKey"
        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = layoutInflater.inflate(R.layout.fragment_node_add, container, false)

    override fun initViews() {
        fearlessToolbar.setHomeButtonListener { viewModel.backClicked() }

        nodeNameField.content.bindTo(viewModel.nodeNameInputLiveData)

        nodeHostField.content.bindTo(viewModel.nodeHostInputLiveData)

        addBtn.setOnClickListener { viewModel.addNodeClicked() }

        addBtn.prepareForProgress(viewLifecycleOwner)
    }

    override fun inject() {
        val chainId = argument<String>(CHAIN_ID_KEY)

        FeatureUtils.getFeature<AccountFeatureComponent>(
            requireContext(),
            AccountFeatureApi::class.java
        )
            .addNodeComponentFactory()
            .create(this, chainId)
            .inject(this)
    }

    override fun subscribe(viewModel: AddNodeViewModel) {
        viewModel.addButtonState.observe {
            addBtn.setState(it.state)
            addBtn.text = it.label
        }
    }
}
