package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.common.compose.theme.errorRed
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.coredb.model.PhishingLocal
import org.junit.Assert.assertEquals
import org.junit.Test

class PhishingModelTest {
    @Test
    fun knownTypesParseCaseInsensitivelyAndUnknownTypesStayUnknown() {
        listOf(PhishingType.SCAM, PhishingType.EXCHANGE, PhishingType.DONATION, PhishingType.SANCTIONS).forEach {
            assertEquals(it, PhishingType.from(it.name))
            assertEquals(it, PhishingType.from(it.name.lowercase()))
        }
        assertEquals(PhishingType.UNKNOWN, PhishingType.from("new-provider-category"))
        assertEquals("Sanctions", PhishingType.SANCTIONS.capitalizedName)
    }

    @Test
    fun scamUsesErrorColorWhileOtherCategoriesUseWarningColor() {
        PhishingType.entries.forEach { type ->
            val model = PhishingModel("address", null, type, null)
            assertEquals(if (type == PhishingType.SCAM) errorRed else warningOrange, model.color)
        }
    }

    @Test
    fun databaseMappingRetainsAddressNameAndSubtype() {
        val local = PhishingLocal("address", "Reported site", "SCAM", "impersonation")
        val model = local.toPhishingModel()
        assertEquals("address", model.address)
        assertEquals("Reported site", model.name)
        assertEquals("impersonation", model.subtype)
        assertEquals(PhishingType.SCAM, model.type)
    }
}
