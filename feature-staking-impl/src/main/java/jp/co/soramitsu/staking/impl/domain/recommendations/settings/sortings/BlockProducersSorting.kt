package jp.co.soramitsu.staking.impl.domain.recommendations.settings.sortings

import java.math.BigDecimal
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.notElected

sealed interface BlockProducersSorting<T> {

    val comparator: Comparator<T>
    var isChecked: Boolean

    sealed interface ValidatorSorting : BlockProducersSorting<Validator> {
        object TotalStakeSorting : ValidatorSorting {
            override val comparator: Comparator<Validator> = Comparator.comparing { validator: Validator ->
                validator.electedInfo?.totalStake ?: notElected(validator.accountIdHex)
            }.reversed()
            override var isChecked: Boolean = false
        }

        object ValidatorOwnStakeSorting : ValidatorSorting {
            override val comparator: Comparator<Validator> = Comparator.comparing { validator: Validator ->
                validator.electedInfo?.ownStake ?: notElected(validator.accountIdHex)
            }.reversed()
            override var isChecked = false
        }

        object APYSorting : ValidatorSorting {
            override val comparator: Comparator<Validator> = Comparator.comparing { validator: Validator ->
                validator.electedInfo?.apy ?: notElected(validator.accountIdHex)
            }.reversed()
            override var isChecked = true
        }
    }

    sealed interface CollatorSorting : BlockProducersSorting<Collator> {
        object EffectiveAmountBondedSorting : CollatorSorting {
            override val comparator: Comparator<Collator> = Comparator.comparing { collator: Collator ->
                collator.totalCounted
            }.reversed()
            override var isChecked = true
        }

        object CollatorsOwnStakeSorting : CollatorSorting {
            override val comparator: Comparator<Collator> = Comparator.comparing { collator: Collator ->
                collator.bond
            }.reversed()
            override var isChecked = true
        }

        object DelegationsSorting : CollatorSorting {
            override val comparator: Comparator<Collator> = Comparator.comparing { collator: Collator ->
                collator.delegationCount
            }.reversed()
            override var isChecked = true
        }

        object APYSorting : CollatorSorting {
            override val comparator: Comparator<Collator> = Comparator.comparing { collator: Collator ->
                collator.apy ?: BigDecimal.ZERO
            }.reversed()

            override var isChecked = false
        }

        object MinimumBondSorting : CollatorSorting {
            override val comparator: Comparator<Collator> = Comparator.comparing { collator: Collator ->
                collator.lowestTopDelegationAmount
            }.reversed()

            override var isChecked = false
        }
    }
}
