package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.fw.FwFontLicense
import jp.co.soramitsu.common.compose.theme.fw.FwTextRole
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.fwTextStyle

/** Scrollable offline notices with a fixed, reachable Close action at large text sizes. */
@Composable
fun FwFontLicensesDialog(licenses: List<FwFontLicense>, onClose: () -> Unit) {
    val colors = LocalFwColors.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A separate dialog window must retain the caller's app locale and text-size overrides.
        CompositionLocalProvider(LocalConfiguration provides configuration, LocalContext provides context, LocalDensity provides density) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp).background(colors.surfaceRaised)
                .padding(16.dp).testTag("fw-font-licenses-dialog")) {
                Text(stringResource(R.string.fw_font_licenses), color = colors.textPrimary, style = fwTextStyle(FwTextRole.TitleM),
                    modifier = Modifier.fillMaxWidth().testTag("fw-font-licenses-title").semantics { heading() }.padding(bottom = 12.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("fw-font-licenses-list")) {
                    for (license in licenses) {
                        item(key = license.name) {
                            Text(license.name, color = colors.textPrimary, style = fwTextStyle(FwTextRole.TitleS),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { heading() }.testTag("fw-font-license-${license.name}"))
                        }
                        items(license.text.split("\n\n")) { paragraph ->
                            SelectionContainer {
                                Text(paragraph, color = colors.textSecondary, style = fwTextStyle(FwTextRole.BodyS),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                            }
                        }
                    }
                    item(key = "end") { Spacer(Modifier.height(1.dp).fillMaxWidth().testTag("fw-font-licenses-end")) }
                }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fw-font-licenses-close")) {
                    Text(stringResource(R.string.common_close), color = colors.textLink, style = fwTextStyle(FwTextRole.LabelL))
                }
            }
        }
    }
}
