package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings

import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.view.isVisible
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentCustomValidatorsSettingsBinding
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@AndroidEntryPoint
class CustomValidatorsSettingsFragment :
    BaseFragment<CustomValidatorsSettingsViewModel>(R.layout.fragment_custom_validators_settings) {

    companion object {
        const val STAKING_TYPE_KEY = "stakingType"
        fun getBundle(stakingType: Chain.Asset.StakingType) = bundleOf(STAKING_TYPE_KEY to stakingType)
    }

    private val filtersAdapter by lazy { SettingsFiltersAdapter(viewModel::onFilterChecked) }
    private val sortingAdapter by lazy { SettingsSortingAdapter(viewModel::onSortingChecked) }
    private val binding by viewBinding(FragmentCustomValidatorsSettingsBinding::bind)

    override val viewModel: CustomValidatorsSettingsViewModel by viewModels()

    override fun initViews() {
        with(binding) {
            customValidatorSettingsApply.setOnClickListener { viewModel.applyChanges() }
            customValidatorSettingsToolbar.setHomeButtonListener { viewModel.backClicked() }
            customValidatorSettingsToolbar.setRightActionClickListener { viewModel.reset() }
            settingsFiltersList.adapter = filtersAdapter
            settingsSortingsList.adapter = sortingAdapter
        }
    }

    override fun subscribe(viewModel: CustomValidatorsSettingsViewModel) {
        viewModel.isResetButtonEnabled.observe(binding.customValidatorSettingsToolbar.rightActionText::setEnabled)
        viewModel.isApplyButtonEnabled.observe {
            binding.customValidatorSettingsApply.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }

//        viewModel.tokenNameFlow.observe {
//            binding.customValidatorSettingsSortTotalStake.text = getString(R.string.staking_validator_total_stake_token, it)
//            binding.customValidatorSettingsSortOwnStake.text = getString(R.string.staking_filter_title_own_stake_token, it)
//        }

        viewModel.settingsSchemaLiveData.observe {
            binding.filtersTitle.isVisible = it.filters.isNotEmpty()
            binding.settingsFiltersList.isVisible = it.filters.isNotEmpty()
            filtersAdapter.submitList(it.filters)
            sortingAdapter.submitList(it.sortings)
        }
    }
}
