package jp.co.soramitsu.common.compose.theme.fw

import jp.co.soramitsu.common.BuildConfig

/** Build-time rollback boundary for migrated UI. Legacy wins over every individual surface flag. */
object FwRestyle {
    fun isEnabled(surface: FwSurface): Boolean = resolve(BuildConfig.FW_PALETTE, surface.requested)

    internal fun resolve(palette: String, requested: Boolean): Boolean = palette == "v1" && requested
}
