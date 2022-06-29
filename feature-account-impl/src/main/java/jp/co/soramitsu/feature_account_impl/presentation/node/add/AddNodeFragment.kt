package jp.co.soramitsu.feature_account_impl.presentation.node.add

import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_api.di.AccountFeatureApi
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodeAddBinding
import jp.co.soramitsu.feature_account_impl.di.AccountFeatureComponent

class AddNodeFragment : BaseFragment<AddNodeViewModel>(R.layout.fragment_node_add) {

    companion object {
        private const val CHAIN_ID_KEY = "chainIdKey"
        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    private val binding by viewBinding(FragmentNodeAddBinding::bind)

    override fun initViews() {
        with(binding) {
            fearlessToolbar.setHomeButtonListener { viewModel.backClicked() }

            nodeNameField.content.bindTo(viewModel.nodeNameInputLiveData)

            nodeHostField.content.bindTo(viewModel.nodeHostInputLiveData)

            addBtn.setOnClickListener { viewModel.addNodeClicked() }

            addBtn.prepareForProgress(viewLifecycleOwner)
        }
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
            binding.addBtn.setState(it.state)
            binding.addBtn.text = it.label
        }
    }
}
