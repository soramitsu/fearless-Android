package jp.co.soramitsu.staking.impl.presentation.collators.change.custom.select

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.scrollToTopWhenItemsShuffled
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentSelectCustomValidatorsBinding
import jp.co.soramitsu.staking.impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.staking.impl.presentation.validators.change.CollatorModel

@AndroidEntryPoint
class SelectCustomCollatorsFragment : BaseFragment<SelectCustomCollatorsViewModel>(R.layout.fragment_select_custom_validators), CollatorsAdapter.ItemHandler {

    private val binding by viewBinding(FragmentSelectCustomValidatorsBinding::bind)

    override val viewModel: SelectCustomCollatorsViewModel by viewModels()

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
        binding.selectCustomCollatorsOnChainIdentity.setOnClickListener {
            viewModel.havingOnChainIdentityFilterClicked()
        }
        binding.selectCustomCollatorsRelevantBond.setOnClickListener {
            viewModel.relevantBondFilterCLicked()
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
        binding.selectCustomCollatorsOnChainIdentity.isVisible = true
        binding.selectCustomCollatorsRelevantBond.isVisible = true
        binding.selectCustomValidatorsClearFilters.isVisible = false
    }

    override fun subscribe(viewModel: SelectCustomCollatorsViewModel) {
        viewModel.collatorModelsFlow.observe {
            when (it) {
                is LoadingState.Loaded -> {
                    binding.recommendedValidatorsProgress.setVisible(false)
                    binding.selectCustomValidatorsList.setVisible(true)
                    binding.selectCustomValidatorsNext.setVisible(true)
                    adapter.submitList(it.data)
                }
                is LoadingState.Loading -> {
                    binding.recommendedValidatorsProgress.setVisible(true)
                    binding.selectCustomValidatorsList.setVisible(false)
                    binding.selectCustomValidatorsNext.setVisible(false)
                }
            }
        }

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
        bindQuickFilter(viewModel.identityFilterEnabled, binding.selectCustomCollatorsOnChainIdentity)
        bindQuickFilter(viewModel.minimumBondFilterEnabled, binding.selectCustomCollatorsRelevantBond)
    }

    private fun bindQuickFilter(liveData: LiveData<Boolean>, view: TextView) {
        liveData.observe {
            view.background = if (it) {
                ContextCompat.getDrawable(requireContext(), R.drawable.primary_chip_background)
            } else {
                ContextCompat.getDrawable(requireContext(), R.drawable.secondary_chip_background)
            }
        }
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
