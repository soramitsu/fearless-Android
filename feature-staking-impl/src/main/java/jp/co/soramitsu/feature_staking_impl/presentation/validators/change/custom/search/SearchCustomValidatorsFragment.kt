package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.search

import android.view.View
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSearchCustomValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent

class SearchCustomValidatorsFragment :
    BaseFragment<SearchCustomValidatorsViewModel>(R.layout.fragment_search_custom_validators),
    CustomBlockProducersAdapter.ItemHandler {

    private val adapter: CustomBlockProducersAdapter by lazy(LazyThreadSafetyMode.NONE) {
        CustomBlockProducersAdapter(this)
    }

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

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .searchCustomValidatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SearchCustomValidatorsViewModel) {
        viewModel.screenState.observe {
            binding.searchCustomValidatorsList.setVisible(it is SearchBlockProducersState.Success, falseState = View.INVISIBLE)
            binding.searchCustomValidatorProgress.setVisible(it is SearchBlockProducersState.Loading, falseState = View.INVISIBLE)
            binding.searchCustomValidatorsPlaceholder.setVisible(it is SearchBlockProducersState.NoResults || it is SearchBlockProducersState.NoInput)
            binding.searchCustomValidatorListHeader.setVisible(it is SearchBlockProducersState.Success)

            when (it) {
                SearchBlockProducersState.NoInput -> {
                    binding.searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_placeholder, 0, 0)
                    binding.searchCustomValidatorsPlaceholder.text = getString(R.string.common_search_start_title)
                }
                SearchBlockProducersState.NoResults -> {
                    binding.searchCustomValidatorsPlaceholder.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_no_search_results, 0, 0)
                    binding.searchCustomValidatorsPlaceholder.text = getString(R.string.staking_validator_search_empty_title)
                }
                SearchBlockProducersState.Loading -> {}
                is SearchBlockProducersState.Success -> {
                    binding.searchCustomValidatorAccounts.text = it.headerTitle

                    adapter.submitList(it.blockProducers)
                }
            }
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
