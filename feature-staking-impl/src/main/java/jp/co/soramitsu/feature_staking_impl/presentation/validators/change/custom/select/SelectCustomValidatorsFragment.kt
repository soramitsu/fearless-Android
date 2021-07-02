package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.select

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.ValidatorModel
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsClearFilters
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsContainer
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsCount
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsDeselectAll
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsFillWithRecommended
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsList
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsNext
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsSorting
import kotlinx.android.synthetic.main.fragment_select_custom_validators.selectCustomValidatorsToolbar

class SelectCustomValidatorsFragment : BaseFragment<SelectCustomValidatorsViewModel>(), ValidatorsAdapter.ItemHandler {

    lateinit var adapter: ValidatorsAdapter

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

        adapter = ValidatorsAdapter(this)
        selectCustomValidatorsList.adapter = adapter
        selectCustomValidatorsList.setHasFixedSize(true)

        selectCustomValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

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

        viewModel.selectedTitle.observe(selectCustomValidatorsCount::setText)

        viewModel.buttonState.observe {
            selectCustomValidatorsNext.text = it.text

            val state = if (it.enabled) ButtonState.NORMAL else ButtonState.DISABLED

            selectCustomValidatorsNext.setState(state)
        }

        viewModel.scoringHeader.observe(selectCustomValidatorsSorting::setText)

        viewModel.fillWithRecommendedEnabled.observe(selectCustomValidatorsFillWithRecommended::setEnabled)
        viewModel.clearFiltersEnabled.observe(selectCustomValidatorsClearFilters::setEnabled)
        viewModel.deselectAllEnabled.observe(selectCustomValidatorsDeselectAll::setEnabled)
    }

    override fun validatorInfoClicked(validatorModel: ValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel)
    }

    override fun validatorClicked(validatorModel: ValidatorModel) {
        viewModel.validatorClicked(validatorModel)
    }
}
