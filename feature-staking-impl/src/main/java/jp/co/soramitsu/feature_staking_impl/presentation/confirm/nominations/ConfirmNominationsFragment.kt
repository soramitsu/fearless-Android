package jp.co.soramitsu.feature_staking_impl.presentation.confirm.nominations

import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentConfirmNominationsBinding
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel

@AndroidEntryPoint
class ConfirmNominationsFragment : BaseFragment<ConfirmNominationsViewModel>(R.layout.fragment_confirm_nominations), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    private val binding by viewBinding(FragmentConfirmNominationsBinding::bind)

    override val viewModel: ConfirmNominationsViewModel by viewModels()

    override fun initViews() {
        adapter = ValidatorsAdapter(this)
        binding.confirmNominationsList.adapter = adapter

        binding.confirmNominationsList.setHasFixedSize(true)

        binding.confirmNominationsToolbar.setHomeButtonListener {
            viewModel.backClicked()
        }
    }

    override fun subscribe(viewModel: ConfirmNominationsViewModel) {
        viewModel.selectedValidatorsLiveData.observe(adapter::submitList)

        viewModel.toolbarTitle.observe(binding.confirmNominationsToolbar::setTitle)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }
}
