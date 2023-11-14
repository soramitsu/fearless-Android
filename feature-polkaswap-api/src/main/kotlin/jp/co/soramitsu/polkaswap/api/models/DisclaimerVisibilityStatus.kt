package jp.co.soramitsu.polkaswap.api.models

@JvmInline
value class DisclaimerVisibilityStatus(
    private val sourceToVisibility: Pair<DisclaimerAppearanceSource, Boolean>
) {

    val source: DisclaimerAppearanceSource
        get() = sourceToVisibility.first

    val visibility: Boolean
        get() = sourceToVisibility.second

}