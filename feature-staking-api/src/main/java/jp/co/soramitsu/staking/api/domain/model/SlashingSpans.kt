package jp.co.soramitsu.staking.api.domain.model

class SlashingSpans(
    val lastNonZeroSlash: EraIndex,
    val prior: List<EraIndex>
)
