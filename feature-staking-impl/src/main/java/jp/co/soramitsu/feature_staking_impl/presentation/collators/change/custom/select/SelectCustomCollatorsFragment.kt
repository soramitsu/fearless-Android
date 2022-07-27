package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select

import androidx.core.view.isVisible
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
import jp.co.soramitsu.feature_staking_impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel

class SelectCustomCollatorsFragment : BaseFragment<SelectCustomCollatorsViewModel>(R.layout.fragment_select_custom_validators), CollatorsAdapter.ItemHandler {

    private val binding by viewBinding(FragmentSelectCustomValidatorsBinding::bind)

    lateinit var adapter: CollatorsAdapter

    override fun initViews() {
        binding.selectCustomValidatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        adapter = CollatorsAdapter(this)
        binding.selectCustomValidatorsList.adapter = adapter
        binding.selectCustomValidatorsList.setHasFixedSize(true)

        binding.selectCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_filterlist_24) {
            viewModel.settingsClicked()
        }

        binding.selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_search_24) {
            viewModel.searchClicked()
        }
        binding.selectCustomValidatorsToolbar.setDividerVisible(false)

        binding.selectCustomValidatorsList.scrollToTopWhenItemsShuffled(viewLifecycleOwner)

        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL).apply {
            setDrawable(requireContext().getDrawableCompat(R.drawable.divider_decoration))
        }
        binding.selectCustomValidatorsList.addItemDecoration(dividerItemDecoration)

//        selectCustomValidatorsFillWithRecommended.setOnClickListener { viewModel.fillRestWithRecommended() }
        binding.selectCustomValidatorsClearFilters.setOnClickListener { viewModel.clearFilters() }
        binding.selectCustomValidatorsDeselectAll.setOnClickListener { viewModel.deselectAll() }

        binding.selectCustomValidatorsNext.setOnClickListener { viewModel.nextClicked() }

        binding.selectCustomValidatorsToolbar.setTitle(R.string.staking_select_collator_title)
        binding.selectCustomValidatorsFillWithRecommended.isVisible = false
        binding.selectCustomValidatorsDeselectAll.isVisible = false
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .selectCustomCollatorsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: SelectCustomCollatorsViewModel) {
        viewModel.collatorModelsFlow.observe(adapter::submitList)

        viewModel.selectedTitle.observe(binding.selectCustomValidatorsCount::setText)

        viewModel.buttonState.observe {
            binding.selectCustomValidatorsNext.text = it.text

            val state = if (it.enabled) ButtonState.NORMAL else ButtonState.DISABLED

            binding.selectCustomValidatorsNext.setState(state)
        }

        viewModel.scoringHeader.observe(binding.selectCustomValidatorsSorting::setText)

//        viewModel.fillWithRecommendedEnabled.observe(selectCustomValidatorsFillWithRecommended::setEnabled)
        viewModel.clearFiltersEnabled.observe(binding.selectCustomValidatorsClearFilters::setEnabled)
        binding.selectCustomValidatorsDeselectAll.isEnabled = viewModel.deselectAllEnabled
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
