package jp.co.soramitsu.featurestakingapi.domain.model

class SlashingSpans(
    val lastNonZeroSlash: EraIndex,
    val prior: List<EraIndex>
)
