package jp.co.soramitsu.feature_staking_impl.presentation.validators.change.recommended

import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.utils.getDrawableCompat
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.viewBinding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentRecommendedValidatorsBinding
import jp.co.soramitsu.feature_staking_impl.presentation.validators.CollatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.change.CollatorModel

@AndroidEntryPoint
class RecommendedCollatorsFragment :
    BaseFragment<RecommendedCollatorsViewModel>(R.layout.fragment_recommended_validators),
    CollatorsAdapter.ItemHandler {

    private val binding by viewBinding(FragmentRecommendedValidatorsBinding::bind)

    override val viewModel: RecommendedCollatorsViewModel by viewModels()

    lateinit var adapter: CollatorsAdapter

    override fun initViews() {
        adapter = CollatorsAdapter(this)
        binding.recommendedValidatorsList.adapter = adapter
        val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL).apply {
            setDrawable(requireContext().getDrawableCompat(R.drawable.divider_decoration))
        }
        binding.recommendedValidatorsList.addItemDecoration(dividerItemDecoration)
        binding.recommendedValidatorsList.setHasFixedSize(true)

        binding.recommendedValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }
        onBackPressed { viewModel.backClicked() }

        binding.recommendedValidatorsNext.setOnClickListener {
            viewModel.nextClicked()
        }
        binding.recommendedValidatorsToolbar.setTitle(viewModel.toolbarTitle)
        binding.recommendedValidatorsToolbar.setDividerVisible(false)
        binding.recommendedValidatorsRewards.text = getString(R.string.staking_rewards_apr)
        binding.recommendedValidatorsNext.text = getString(R.string.staking_select_collator_title)
    }

    override fun subscribe(viewModel: RecommendedCollatorsViewModel) {
        viewModel.recommendedCollatorModels.observe {
            adapter.submitList(it)

            val selectedAny = it.any { collator -> collator.isChecked == true }
            val selectedText = "${getString(R.string.common_selected)}: ${if (selectedAny) 1 else ""}"
            binding.recommendedValidatorsProgress.setVisible(false)
            binding.recommendedValidatorsNext.setVisible(true)
            binding.recommendedValidatorsNext.isEnabled = it.isNotEmpty() && selectedAny
            binding.recommendedValidatorsList.setVisible(true)
            binding.recommendedValidatorsAccounts.text = selectedText
        }

        viewModel.selectedTitle.observe(binding.recommendedValidatorsAccounts::setText)
    }

    override fun collatorInfoClicked(collatorModel: CollatorModel) {
        viewModel.collatorInfoClicked(collatorModel)
    }

    override fun collatorClicked(collatorModel: CollatorModel) {
        viewModel.collatorClicked(collatorModel)
    }
}
