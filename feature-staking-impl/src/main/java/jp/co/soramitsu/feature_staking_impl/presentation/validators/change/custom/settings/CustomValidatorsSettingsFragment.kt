package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings

import android.widget.CompoundButton
import androidx.lifecycle.lifecycleScope
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.bindFromMap
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentCustomValidatorsSettingsBinding
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationPostProcessor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.HasIdentityFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotOverSubscribedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotSlashedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.postprocessors.RemoveClusteringPostprocessor

class CustomValidatorsSettingsFragment : BaseFragment<CustomValidatorsSettingsViewModel>(R.layout.fragment_custom_validators_settings) {

    private val binding by viewBinding(FragmentCustomValidatorsSettingsBinding::bind)

    override fun initViews() {
        with(binding) {
            customValidatorSettingsApply.setOnClickListener { viewModel.applyChanges() }

            customValidatorSettingsToolbar.setHomeButtonListener { viewModel.backClicked() }
            customValidatorSettingsToolbar.setRightActionClickListener { viewModel.reset() }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .customValidatorsSettingsComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CustomValidatorsSettingsViewModel) {
        with(binding) {
            customValidatorSettingsSort.bindTo(viewModel.selectedSortingIdFlow, lifecycleScope)

            customValidatorSettingsFilterIdentity.bindFilter(HasIdentityFilter::class.java)
            customValidatorSettingsFilterSlashes.bindFilter(NotSlashedFilter::class.java)
            customValidatorSettingsFilterOverSubscribed.bindFilter(NotOverSubscribedFilter::class.java)
            customValidatorSettingsFilterClustering.bindPostProcessor(RemoveClusteringPostprocessor::class.java)
        }

        viewModel.isResetButtonEnabled.observe(binding.customValidatorSettingsToolbar.rightActionText::setEnabled)
        viewModel.isApplyButtonEnabled.observe {
            binding.customValidatorSettingsApply.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }

        viewModel.tokenNameFlow.observe {
            binding.customValidatorSettingsSortTotalStake.text = getString(R.string.staking_validator_total_stake_token, it)
            binding.customValidatorSettingsSortOwnStake.text = getString(R.string.staking_filter_title_own_stake_token, it)
        }
    }

    private fun CompoundButton.bindPostProcessor(postProcessorClass: Class<out RecommendationPostProcessor>) {
        bindFromMap(postProcessorClass, viewModel.postProcessorsEnabledMap, lifecycleScope)
    }

    private fun CompoundButton.bindFilter(filterClass: Class<out RecommendationFilter>) {
        bindFromMap(filterClass, viewModel.filtersEnabledMap, lifecycleScope)
    }
}
