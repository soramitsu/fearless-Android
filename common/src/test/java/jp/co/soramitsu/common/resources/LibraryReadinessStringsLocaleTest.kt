package jp.co.soramitsu.common.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class LibraryReadinessStringsLocaleTest {
    @Test fun productionNavigationPortfolioAndSafetyCopyExistsInEveryShippedLocaleWithExactArguments() {
        val root = File("src/main/res")
        val original = entries(File(root, "values/strings.xml"))
        for (locale in listOf("in", "ja", "pt", "ru", "tr", "vi", "zh")) {
            val translated = entries(File(root, "values-$locale/strings.xml"))
            for (key in KEYS) {
                val english = checkNotNull(original[key])
                val localized = checkNotNull(translated[key]) { "$locale missing $key" }
                assertEquals("$locale $key type", english.tagName, localized.tagName)
                val texts = if (localized.tagName == "plurals") {
                    val items = localized.getElementsByTagName("item")
                    val quantities = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }
                    assertEquals(quantities.size, quantities.toSet().size)
                    assertTrue("$locale $key needs other", "other" in quantities)
                    if (locale == "ru") assertTrue(quantities.containsAll(listOf("one", "few", "many", "other")))
                    (0 until items.length).map { items.item(it).textContent }
                } else listOf(localized.textContent)
                val base = if (english.tagName == "plurals") english.getElementsByTagName("item").item(0).textContent else english.textContent
                texts.forEach {
                    assertTrue("$locale $key blank", it.isNotBlank())
                    assertEquals("$locale $key arguments", arguments(base), arguments(it))
                }
            }
            val joinLabel = checkNotNull(translated[POOL_JOIN_LABEL]).textContent
            val guidance = checkNotNull(translated["ux_pool_operator_description"]).textContent
            assertTrue("$locale pool guidance must name the visible Join pool action", guidance.contains(joinLabel))
        }
    }

    private fun entries(file: File): Map<String, Element> {
        val children = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        val values = linkedMapOf<String, Element>()
        for (index in 0 until children.length) {
            val node = children.item(index) as? Element ?: continue
            val key = node.getAttribute("name")
            if (key !in KEYS && key != POOL_JOIN_LABEL) continue
            assertFalse("duplicate $key in $file", values.containsKey(key))
            values[key] = node
        }
        return values
    }

    private fun arguments(value: String): List<String> = Regex("%(?:[0-9]+\\$)?[sd]").findAll(value).map { it.value }.sorted().toList()

    private val POOL_JOIN_LABEL = "staking_pool_start_join_button_title"

    private val KEYS = setOf(
        "cross_chain_route_checking",
        "cross_chain_protocol_xcm",
        "cross_chain_no_supported_route",
        "cross_chain_route_protocol",
        "cross_chain_route_minimum_value",
        "cross_chain_route_minimum_unknown",
        "cross_chain_route_origin_fee",
        "cross_chain_route_destination_fee",
        "cross_chain_route_estimated_time",
        "cross_chain_route_estimated_time_unknown",
        "cross_chain_route_warning",
        "connection_add_ton_account",
        "connection_add_signing_account",
        "tabbar_portfolio_title",
        "tabbar_defi_title",
        "tabbar_cross_chain_title",
        "settings_section_wallets_accounts",
        "settings_section_networks_assets",
        "settings_section_connections",
        "settings_section_security",
        "settings_section_preferences",
        "settings_manage_assets",
        "portfolio_detected_assets",
        "portfolio_detected_assets_description",
        "portfolio_price_unavailable",
        "portfolio_asset_verified",
        "portfolio_asset_unverified",
        "portfolio_asset_metadata_missing",
        "portfolio_sync_updated",
        "portfolio_sync_stale_since",
        "portfolio_sync_error",
        "portfolio_sync_pending",
        "portfolio_sync_limited",
        "common_collapse",
        "common_expand",
        "portfolio_assets_count",
        "ux_choose_network",
        "ux_network_selection_title",
        "ux_network_selection_description",
        "ux_networks_ethereum_polkadot",
        "ux_assets_ethereum_polkadot",
        "ux_network_ton",
        "ux_asset_ton",
        "ux_receive_network",
        "ux_receive_instruction",
        "ux_receive_qr",
        "ux_backup_status",
        "ux_backup_description",
        "ux_swap_direction",
        "ux_execution_details",
        "ux_show_details",
        "ux_hide_details",
        "ux_switch_wallet",
        "ux_scan",
        "ux_search",
        "ux_more",
        "ux_filter",
        "ux_sort",
        "ux_close",
        "ux_copy",
        "ux_share",
        "ux_send_maximum",
        "ux_send_maximum_description",
        "ux_staking_estimate",
        "ux_staking_variability",
        "ux_pool_operator_options",
        "ux_pool_operator_description",
        "ux_tab_wallet",
        "ux_tab_earn",
        "ux_tab_swap",
        "ux_tab_transfer",
        "ux_network_transfer",
        "ux_back",
        "ux_swap_settings"
    )
}
