package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings

import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.notElected

interface BlockProducersSorting<T> : Comparator<T> {

    interface ValidatorSorting : BlockProducersSorting<Validator> {
        object TotalStakeSorting : ValidatorSorting by Comparator.comparing({ validator: Validator ->
            validator.electedInfo?.totalStake ?: notElected(validator.accountIdHex)
        }).reversed() as ValidatorSorting

        object ValidatorOwnStakeSorting : ValidatorSorting by Comparator.comparing({ validator: Validator ->
            validator.electedInfo?.ownStake ?: notElected(validator.accountIdHex)
        }).reversed() as ValidatorSorting

        object APYSorting : ValidatorSorting by Comparator.comparing({ validator: Validator ->
            validator.electedInfo?.apy ?: notElected(validator.accountIdHex)
        }).reversed() as ValidatorSorting
    }

    interface CollatorSorting : BlockProducersSorting<Collator> {
        object EffectiveAmountBondedSorting : CollatorSorting by Comparator.comparing({ collator: Collator ->
            collator.totalCounted
        }).reversed() as CollatorSorting

        object APYSorting : CollatorSorting by Comparator.comparing({ collator: Collator ->
            collator.apy ?: notElected(collator.address)
        }).reversed() as CollatorSorting
    }
}
