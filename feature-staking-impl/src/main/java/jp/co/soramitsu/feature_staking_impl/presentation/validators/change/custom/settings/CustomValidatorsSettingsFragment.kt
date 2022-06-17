package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.custom.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.android.synthetic.main.fragment_custom_validators_settings.customValidatorSettingsApply
import kotlinx.android.synthetic.main.fragment_custom_validators_settings.customValidatorSettingsToolbar
import kotlinx.android.synthetic.main.fragment_custom_validators_settings.settingsFiltersList
import kotlinx.android.synthetic.main.fragment_custom_validators_settings.settingsSortingsList

class CustomValidatorsSettingsFragment : BaseFragment<CustomValidatorsSettingsViewModel>() {

    companion object {
        private const val STAKING_TYPE_KEY = "stakingType"
        fun getBundle(stakingType: Chain.Asset.StakingType) = bundleOf(STAKING_TYPE_KEY to stakingType)
    }

    private val filtersAdapter by lazy { SettingsFiltersAdapter(viewModel::onFilterChecked) }
    private val sortingAdapter by lazy { SettingsSortingAdapter(viewModel::onSortingChecked) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_custom_validators_settings, container, false)
    }

    override fun initViews() {
        customValidatorSettingsApply.setOnClickListener { viewModel.applyChanges() }

        customValidatorSettingsToolbar.setHomeButtonListener { viewModel.backClicked() }
        customValidatorSettingsToolbar.setRightActionClickListener { viewModel.reset() }
        settingsFiltersList.adapter = filtersAdapter
        settingsSortingsList.adapter = sortingAdapter
    }

    override fun inject() {
        val type = (arguments?.get(STAKING_TYPE_KEY) as? Chain.Asset.StakingType) ?: error("There are no settings passed in settings screen")
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .customValidatorsSettingsComponentFactory()
            .create(this, type)
            .inject(this)
    }

    override fun subscribe(viewModel: CustomValidatorsSettingsViewModel) {
        viewModel.isResetButtonEnabled.observe(customValidatorSettingsToolbar.rightActionText::setEnabled)
        viewModel.isApplyButtonEnabled.observe {
            customValidatorSettingsApply.setState(if (it) ButtonState.NORMAL else ButtonState.DISABLED)
        }

//        viewModel.tokenNameFlow.observe {
//            customValidatorSettingsSortTotalStake.text = getString(R.string.staking_validator_total_stake_token, it)
//            customValidatorSettingsSortOwnStake.text = getString(R.string.staking_filter_title_own_stake_token, it)
//        }

        viewModel.settingsSchemaLiveData.observe {
            filtersAdapter.submitList(it.filters)
            sortingAdapter.submitList(it.sortings)
        }
    }
}
