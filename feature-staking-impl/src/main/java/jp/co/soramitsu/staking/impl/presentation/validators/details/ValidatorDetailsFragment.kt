package jp.co.soramitsu.staking.impl.presentation.validators.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import jp.co.soramitsu.account.api.presentation.actions.setupExternalActions
import jp.co.soramitsu.common.base.BaseBottomSheetDialogFragment
import jp.co.soramitsu.common.utils.createSendEmailIntent
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.FragmentValidatorDetailsBinding
import jp.co.soramitsu.staking.impl.presentation.validators.parcel.ValidatorDetailsParcelModel

@AndroidEntryPoint
class ValidatorDetailsFragment : BaseBottomSheetDialogFragment<ValidatorDetailsViewModel>(R.layout.fragment_validator_details) {

    companion object {
        const val KEY_VALIDATOR = "validator"

        fun getBundle(validator: ValidatorDetailsParcelModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_VALIDATOR, validator)
            }
        }
    }

    override val viewModel: ValidatorDetailsViewModel by viewModels()

    private lateinit var binding: FragmentValidatorDetailsBinding

    override fun provideView(): View {
        val inflater = LayoutInflater.from(requireContext())
        binding = FragmentValidatorDetailsBinding.inflate(inflater)
        return binding.root
    }

    override fun initViews() {
        with(binding) {
            validatorDetailsToolbar.setHomeButtonListener { viewModel.backClicked() }

            validatorInfo.setTotalStakeClickListener {
                viewModel.totalStakeClicked()
            }

            validatorIdentity.setEmailClickListener {
                viewModel.emailClicked()
            }

            validatorIdentity.setWebClickListener {
                viewModel.webClicked()
            }

            validatorIdentity.setTwitterClickListener {
                viewModel.twitterClicked()
            }

            validatorAccountInfo.setWholeClickListener { viewModel.accountActionsClicked() }
        }
    }

    override fun subscribe(viewModel: ValidatorDetailsViewModel) {
        setupExternalActions(viewModel)

        viewModel.validatorDetails.observe { validator ->
            with(validator.stake) {
                binding.validatorInfo.visibility = View.VISIBLE
                binding.validatorInfo.setStatus(statusText, statusColorRes)

                if (activeStakeModel != null) {
                    with(binding) {
                        validatorInfo.showActiveStakeFields()

                        validatorInfo.setNominatorsCount(activeStakeModel.nominatorsCount, activeStakeModel.maxNominations)
                        validatorInfo.setEstimatedRewardApy(activeStakeModel.apy)
                        validatorInfo.setTotalStakeValue(activeStakeModel.totalStake)
                        validatorInfo.setTotalStakeValueFiat(activeStakeModel.totalStakeFiat)
                    }
                } else {
                    binding.validatorInfo.hideActiveStakeFields()
                }
            }

            if (validator.identity == null || validator.identity.isEmptyExceptName) {
                binding.validatorIdentity.makeGone()
            } else {
                binding.validatorIdentity.makeVisible()
                binding.validatorIdentity.populateIdentity(validator.identity)
            }

            binding.validatorAccountInfo.setAccountIcon(validator.addressImage)

            if (validator.identity?.display == null) {
                binding.validatorAccountInfo.setTitle(validator.address)
                binding.validatorAccountInfo.hideBody()
            } else {
                binding.validatorAccountInfo.setTitle(validator.identity.display)
                binding.validatorAccountInfo.setText(validator.address)
                binding.validatorAccountInfo.showBody()
            }
        }

        viewModel.errorFlow.observe {
            it?.let { binding.validatorInfo.setErrors(it) }
        }

        viewModel.openEmailEvent.observeEvent {
            requireContext().createSendEmailIntent(it, getString(R.string.common_email_chooser_title))
        }

        viewModel.totalStakeEvent.observeEvent {
            ValidatorStakeBottomSheet(requireContext(), it).show()
        }
    }
}
