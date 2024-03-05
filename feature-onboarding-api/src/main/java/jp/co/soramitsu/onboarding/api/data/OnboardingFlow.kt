package jp.co.soramitsu.onboarding.api.data

data class OnboardingConfig(
    val en_EN: Variants
) {
    class Variants(
        val new: List<ScreenInfo>,
        val regular: List<ScreenInfo>
    ) {
        class ScreenInfo(
            val title: String,
            val description: String,
            val image: String
        ) {
            companion object;
        }
        companion object;
    }
    companion object;
}