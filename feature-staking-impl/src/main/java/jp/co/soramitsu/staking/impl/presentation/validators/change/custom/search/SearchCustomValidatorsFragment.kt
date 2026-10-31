package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.search

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSearchCustomValidatorsBinding

@AndroidEntryPoint
class SearchCustomValidatorsFragment :
    BaseFragment<SearchCustomValidatorsViewModel>(R.layout.fragment_search_custom_validators),
    CustomBlockProducersAdapter.ItemHandler {

    private val adapter: CustomBlockProducersAdapter by lazy(LazyThreadSafetyMode.NONE) {
        CustomBlockProducersAdapter(this)
    }

    override val viewModel: SearchCustomValidatorsViewModel by viewModels()

    private val binding by viewBinding(FragmentSearchCustomValidatorsBinding::bind)

    override fun initViews() {
        with(binding) {
            searchCustomValidatorsContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }

                consume(true)
            }

            searchCustomValidatorsList.adapter = adapter
            searchCustomValidatorsList.setHasFixedSize(true)

            searchCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
            onBackPressed { viewModel.backClicked() }

            searchCustomValidatorsToolbar.setRightActionClickListener {
                viewModel.doneClicked()
            }
        }
    }

    override fun subscribe(viewModel: SearchCustomValidatorsViewModel) {
        viewModel.screenState.observe { state ->
            binding.renderValidatorSearchState(state, viewModel::retryCatalogue) { adapter.submitList(it) }
        }

        binding.searchCustomValidatorsField.bindTo(viewModel.enteredQuery, viewLifecycleOwner.lifecycleScope)
    }

    override fun blockProducerInfoClicked(blockProducerModel: SearchBlockProducerModel) {
        viewModel.blockProducerInfoClicked(blockProducerModel)
    }

    override fun blockProducerClicked(blockProducerModel: SearchBlockProducerModel) {
        viewModel.blockProducerClicked(blockProducerModel)
    }
}
