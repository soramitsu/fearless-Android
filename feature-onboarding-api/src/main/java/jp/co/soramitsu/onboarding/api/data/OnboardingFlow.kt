package jp.co.soramitsu.onboarding.api.data

data class OnboardingConfig(
    val configs: List<OnboardingConfigItem>
) {
    data class OnboardingConfigItem(
        val minVersion: String,
        val background: String,
        val enEn: Variants
    ) {
        class Variants(
            val new: List<ScreenInfo>,
            val regular: List<ScreenInfo>
        ) {
            class ScreenInfo(
                val title: TitleInfo,
                val description: String,
                val image: String
            ) {
                class TitleInfo(
                    val text: String,
                    val color: String
                ) {
                    companion object;
                }
                companion object;
            }
            companion object;
        }
        companion object;
    }
    companion object;
}