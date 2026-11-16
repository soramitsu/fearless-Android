package jp.co.soramitsu.onboarding.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OnboardingConfigTest {

    private val title = OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo.TitleInfo(text = "Networks", color = "#e6006f")
    private val screen = OnboardingConfig.OnboardingConfigItem.Variants.ScreenInfo(
        title = title,
        description = "Pick your networks",
        image = "https://example.invalid/slide.png"
    )
    private val variants = OnboardingConfig.OnboardingConfigItem.Variants(new = listOf(screen), regular = emptyList())

    @Test
    fun slidesKeepTheirTitleDescriptionAndImage() {
        assertEquals("Networks", screen.title.text)
        assertEquals("#e6006f", screen.title.color)
        assertEquals("Pick your networks", screen.description)
        assertEquals("https://example.invalid/slide.png", screen.image)
        assertSame(screen, variants.new.single())
        assertEquals(emptyList<Any>(), variants.regular)
    }

    @Test
    fun configItemsCompareByValue() {
        val item = OnboardingConfig.OnboardingConfigItem(minVersion = "4.3.0", background = "bg", enEn = variants)
        val same = OnboardingConfig.OnboardingConfigItem(minVersion = "4.3.0", background = "bg", enEn = variants)
        val config = OnboardingConfig(configs = listOf(item))

        assertEquals("4.3.0", item.minVersion)
        assertEquals("bg", item.background)
        assertSame(variants, item.enEn)
        assertEquals(item, same)
        assertEquals(item.hashCode(), same.hashCode())
        assertNotEquals(item, item.copy(minVersion = "4.4.0"))
        assertEquals(OnboardingConfig(listOf(same)), config)
        assertEquals(listOf(item), config.configs)
        assertEquals(OnboardingConfig(emptyList()), config.copy(configs = emptyList()))
    }
}
