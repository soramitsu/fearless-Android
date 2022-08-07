package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select

import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.scrollToTopWhenItemsShuffled
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSelectCustomValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel

class SelectCustomValidatorsFragment :
    BaseFragment<SelectCustomValidatorsViewModel>(R.layout.fragment_select_custom_validators),
    ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

    private val binding by viewBinding(FragmentSelectCustomValidatorsBinding::bind)

    override fun initViews() {
        adapter = ValidatorsAdapter(this)

        with(binding) {
            selectCustomValidatorsContainer.applyInsetter {
                type(statusBars = true) {
                    padding()
                }
            }

            onBackPressed { viewModel.backClicked() }

            selectCustomValidatorsList.adapter = adapter
            selectCustomValidatorsList.setHasFixedSize(true)

            selectCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }

            selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_filterlist_24) {
                viewModel.settingsClicked()
            }

            selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_search_24) {
                viewModel.searchClicked()
            }

            selectCustomValidatorsList.scrollToTopWhenItemsShuffled(viewLifecycleOwner)

            val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL).apply {
                setDrawable(requireContext().getDrawableCompat(R.drawable.divider_decoration))
            }

            selectCustomValidatorsList.addItemDecoration(dividerItemDecoration)

            selectCustomValidatorsFillWithRecommended.setOnClickListener { viewModel.fillRestWithRecommended() }
            selectCustomValidatorsClearFilters.setOnClickListener { viewModel.clearFilters() }
            selectCustomValidatorsDeselectAll.setOnClickListener { viewModel.deselectAll() }

            selectCustomValidatorsNext.setOnClickListener { viewModel.nextClicked() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectCustomValidatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectCustomValidatorsViewModel) {
        viewModel.validatorModelsFlow.observe(adapter::submitList)

        viewModel.selectedTitle.observe(binding.selectCustomValidatorsCount::setText)

        viewModel.buttonState.observe {
            binding.selectCustomValidatorsNext.text = it.text

            val state = if (it.enabled) ButtonState.NORMAL else ButtonState.DISABLED

            binding.selectCustomValidatorsNext.setState(state)
        }

        viewModel.scoringHeader.observe(binding.selectCustomValidatorsSorting::setText)

        viewModel.fillWithRecommendedEnabled.observe(binding.selectCustomValidatorsFillWithRecommended::setEnabled)
        viewModel.clearFiltersEnabled.observe(binding.selectCustomValidatorsClearFilters::setEnabled)
        viewModel.deselectAllEnabled.observe(binding.selectCustomValidatorsDeselectAll::setEnabled)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }

    override fun validatorClicked(validatorModel: ValidatorModel) {
        viewModel.validatorClicked(validatorModel)
    }
}
