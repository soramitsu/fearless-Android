package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

import jp.co.soramitsu.common.validation.ValidationSystem

const val SYSTEM_MANAGE_STAKING_DEFAULT = "ManageStakingDefault"
const val SYSTEM_MANAGE_STAKING_UNBOND = "ManageStakingUnbond"

typealias ManageStakingValidationSystem = ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
