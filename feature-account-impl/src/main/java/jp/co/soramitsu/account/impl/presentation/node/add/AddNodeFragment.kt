package jp.co.soramitsu.account.impl.presentation.node.add

import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.FragmentNodeAddBinding

@AndroidEntryPoint
class AddNodeFragment : BaseFragment<AddNodeViewModel>(R.layout.fragment_node_add) {

    companion object {
        const val CHAIN_ID_KEY = "chainIdKey"
        fun getBundle(chainId: String) = bundleOf(CHAIN_ID_KEY to chainId)
    }

    private val binding by viewBinding(FragmentNodeAddBinding::bind)

    override val viewModel: AddNodeViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            fearlessToolbar.setHomeButtonListener { viewModel.backClicked() }

            nodeNameField.content.bindTo(viewModel.nodeNameInputLiveData)

            nodeHostField.content.bindTo(viewModel.nodeHostInputLiveData)

            addBtn.setOnClickListener { viewModel.addNodeClicked() }

            addBtn.prepareForProgress(viewLifecycleOwner)
        }
    }

    override fun subscribe(viewModel: AddNodeViewModel) {
        viewModel.addButtonState.observe {
            binding.addBtn.setState(it.state)
            binding.addBtn.text = it.label
        }
    }
}
