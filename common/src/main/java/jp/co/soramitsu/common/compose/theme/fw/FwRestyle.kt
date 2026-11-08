package jp.co.soramitsu.common.compose.theme.fw

import jp.co.soramitsu.common.BuildConfig

/** Build-time rollback boundary for migrated UI. Legacy wins over every individual surface flag. */
object FwRestyle {
    fun isEnabled(surface: FwSurface): Boolean = resolve(BuildConfig.FW_PALETTE, surface.requested)

    /**
     * Shared building blocks (buttons, cards, inputs, sheets, navigation chrome, page light) follow
     * the palette alone: FW_PALETTE=legacy restores every one of them at once.
     */
    val paletteEnabled: Boolean get() = resolvePalette(BuildConfig.FW_PALETTE)

    internal fun resolve(palette: String, requested: Boolean): Boolean = resolvePalette(palette) && requested

    internal fun resolvePalette(palette: String): Boolean = palette == "v1"
}
