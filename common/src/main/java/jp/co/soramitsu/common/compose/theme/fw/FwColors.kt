// FwColors.kt: GENERATED from config/design/fw.tokens.json; do not edit.
// sha256 18180aa767b87b8ac0bb767622e172328d95145817a1475a840905eec55b4315
// Regenerate: node scripts/design/gen-fw-tokens.mjs --android <checkout>
package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class FwColors(
    val bgBase: Color,
    val surfaceRaised: Color,
    val surfaceRaised2: Color,
    val surfacePressed: Color,
    val surfaceSunken: Color,
    val glass: Color,
    val glassSheen: Color,
    val glassElevated: Color,
    val glassElevatedSheen: Color,
    val chrome: Color,
    val chromeSheen: Color,
    val chromeSolid: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textOnAccent: Color,
    val textLink: Color,
    val textBrand: Color,
    val textPromo: Color,
    val brand: Color,
    val fillTop: Color,
    val fillBottom: Color,
    val accentEdge: Color,
    val focus: Color,
    val violet: Color,
    val priceUp: Color,
    val priceDown: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val dangerFill: Color,
    val selectFill: Color,
    val selectEdge: Color,
    val rowSelected: Color,
    val divider: Color,
    val strokeStrong: Color,
    val strokeControl: Color,
    val hudTick: Color,
    val hudTrace: Color,
    val thumbOff: Color,
    val qrModule: Color,
    val qrField: Color,
)

val FwStandard = FwColors(
    bgBase = Color(0xFF0A0C1B),
    surfaceRaised = Color(0xFF12152B),
    surfaceRaised2 = Color(0xFF1A1E3A),
    surfacePressed = Color(0xFF242A4D),
    surfaceSunken = Color(0xFF06070F),
    glass = Color(0xB8141833),
    glassSheen = Color(0x0AFFFFFF),
    glassElevated = Color(0xDB1A1E3A),
    glassElevatedSheen = Color(0x0DFFFFFF),
    chrome = Color(0xE00E1126),
    chromeSheen = Color(0x08FFFFFF),
    chromeSolid = Color(0xF50E1126),
    scrim = Color(0xB804050D),
    textPrimary = Color(0xFFEDF0FA),
    textSecondary = Color(0xFFB8BEDB),
    textTertiary = Color(0xFF9198BC),
    textDisabled = Color(0xFF5E6487),
    textOnAccent = Color(0xFFFFFFFF),
    textLink = Color(0xFF2EE6FF),
    textBrand = Color(0xFFFF4DB8),
    textPromo = Color(0xFFA99BFF),
    brand = Color(0xFFFF009A),
    fillTop = Color(0xFFD6007F),
    fillBottom = Color(0xFFA3006A),
    accentEdge = Color(0x8CFF4DB8),
    focus = Color(0xFF2EE6FF),
    violet = Color(0xFF8B5CFF),
    priceUp = Color(0xFF38E8E0),
    priceDown = Color(0xFFFFA057),
    success = Color(0xFF3DF2B0),
    warning = Color(0xFFFFC452),
    danger = Color(0xFFFF7466),
    info = Color(0xFF2EE6FF),
    dangerFill = Color(0xFFB3302A),
    selectFill = Color(0x29FF009A),
    selectEdge = Color(0xFFFF4DB8),
    rowSelected = Color(0x1AFF009A),
    divider = Color(0x14FFFFFF),
    strokeStrong = Color(0x47FFFFFF),
    strokeControl = Color(0x66FFFFFF),
    hudTick = Color(0x992EE6FF),
    hudTrace = Color(0x662EE6FF),
    thumbOff = Color(0xFFC9CEE8),
    qrModule = Color(0xFF0A0C1B),
    qrField = Color(0xFFFFFFFF),
)

val FwHighContrast = FwStandard.copy(
    textSecondary = Color(0xFFD5DAEA),
    textTertiary = Color(0xFFBAC0D4),
    strokeControl = Color(0x8CFFFFFF),
)

/** status.*.container: the status colour at 14 % over the current surface (info: FW_INFO_CONTAINER_ALPHA). */
fun Color.container(alpha: Float = 0.14f) = copy(alpha = alpha)

const val FW_INFO_CONTAINER_ALPHA = 0.12f

object FwBrush {
    val Hairline = Brush.linearGradient(0f to Color(0x38FFFFFF), 0.42f to Color(0x0FFFFFFF), 1f to Color(0x47FF009A))
    val Accent = Brush.linearGradient(0f to Color(0xBFFF009A), 0.5f to Color(0x8C8B5CFF), 1f to Color(0xA62EE6FF))
    val PrimaryFill = Brush.verticalGradient(listOf(Color(0xFFD6007F), Color(0xFFA3006A)))
}
