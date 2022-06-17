package jp.co.soramitsu.feature_staking_impl.presentation.collators.change.custom.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.scrollToTopWhenItemsShuffled
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsClearFilters
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsContainer
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsCount
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsDeselectAll
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsFillWithRecommended
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsList
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsNext
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsSorting
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsToolbar

class SelectCustomCollatorsFragment : BaseFragment<SelectCustomCollatorsViewModel>(), CollatorsAdapter.ItemHandler {

    lateinit var adapter: CollatorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_select_custom_validators, container, false)
    }

    override fun initViews() {
        selectCustomValidatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        adapter = CollatorsAdapter(this)
        selectCustomValidatorsList.adapter = adapter
        selectCustomValidatorsList.setHasFixedSize(true)

        selectCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_filterlist_24) {
            viewModel.settingsClicked()
        }

        // todo fix search
//        selectCustomValidatorsToolbar.addCustomAction(R.drawable.ic_basic_search_24) {
//            viewModel.searchClicked()
//        }

        selectCustomValidatorsList.scrollToTopWhenItemsShuffled(viewLifecycleOwner)

        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL).apply {
            setDrawable(requireContext().getDrawableCompat(R.drawable.divider_decoration))
        }
        selectCustomValidatorsList.addItemDecoration(dividerItemDecoration)

//        selectCustomValidatorsFillWithRecommended.setOnClickListener { viewModel.fillRestWithRecommended() }
        selectCustomValidatorsClearFilters.setOnClickListener { viewModel.clearFilters() }
        selectCustomValidatorsDeselectAll.setOnClickListener { viewModel.deselectAll() }

        selectCustomValidatorsNext.setOnClickListener { viewModel.nextClicked() }

        selectCustomValidatorsToolbar.setTitle(R.string.staking_select_collator_title)
        selectCustomValidatorsFillWithRecommended.isVisible = false
        selectCustomValidatorsDeselectAll.isVisible = false
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

        viewModel.selectedTitle.observe(selectCustomValidatorsCount::setText)

        viewModel.buttonState.observe {
            selectCustomValidatorsNext.text = it.text

            val state = if (it.enabled) ButtonState.NORMAL else ButtonState.DISABLED

            selectCustomValidatorsNext.setState(state)
        }

        viewModel.scoringHeader.observe(selectCustomValidatorsSorting::setText)

//        viewModel.fillWithRecommendedEnabled.observe(selectCustomValidatorsFillWithRecommended::setEnabled)
        viewModel.clearFiltersEnabled.observe(selectCustomValidatorsClearFilters::setEnabled)
        selectCustomValidatorsDeselectAll.isEnabled = viewModel.deselectAllEnabled
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
