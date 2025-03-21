package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.backgroundBlack
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black72

@Composable
fun BottomSheetScreen(
    modifier: Modifier = Modifier,
    Content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.Bottom) {
        MarginVertical(margin = 12.dp)
        Column(
            modifier = modifier.background(backgroundBlack, RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp))
        ) {
            MarginVertical(margin = 2.dp)
            Grip(Modifier.align(Alignment.CenterHorizontally))
            MarginVertical(margin = 8.dp)
            Content()
        }
    }
}

@Composable
fun BottomSheetDialog(modifier: Modifier = Modifier, verticalArrangement: Arrangement.Vertical = Arrangement.Top, Content: @Composable ColumnScope.() -> Unit) {
    val sheetBackgroundModifier = Modifier.background(backgroundBlack, RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp))
    Column {
        MarginVertical(margin = 12.dp)
        Column(
            modifier = sheetBackgroundModifier.then(modifier)
        ) {
            MarginVertical(margin = 2.dp)
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = verticalArrangement) {
                    MarginVertical(margin = 12.dp)
                    Content()
                }
                Grip(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetLayout(
    content: @Composable (ModalBottomSheetState) -> Unit,
    sheetContent: @Composable ColumnScope.(ModalBottomSheetState) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    ModalBottomSheetLayout(
        modifier = modifier,
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp),
        sheetBackgroundColor = backgroundBlack,
        scrimColor = black72,
        sheetContent = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sheetContent(bottomSheetState)
                }
                Grip(Modifier.align(Alignment.TopCenter))
            }
        },
        content = { content(bottomSheetState) }
    )
}

@Composable
fun BottomSheetLayout(
    content: @Composable (ModalBottomSheetState) -> Unit,
    sheetContent: @Composable ColumnScope.(ModalBottomSheetState) -> Unit,
    bottomSheetState: ModalBottomSheetState,
    sheetGesturesEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ModalBottomSheetLayout(
        modifier = modifier,
        sheetState = bottomSheetState,
        sheetShape = RoundedCornerShape(topEnd = 24.dp, topStart = 24.dp),
        sheetBackgroundColor = backgroundBlack,
        scrimColor = black72,
        sheetGesturesEnabled = sheetGesturesEnabled,
        sheetContent = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    sheetContent(bottomSheetState)
                }
                Grip(Modifier.align(Alignment.TopCenter))
            }
        },
        content = { content(bottomSheetState) }
    )
}

@Composable
fun Grip(modifier: Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .width(35.dp)
            .background(black1, RoundedCornerShape(80.dp))
    )
}
