package jp.co.soramitsu.feature_account_impl.presentation.about

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.soramitsu.common.presentation.LoadingState

@Composable
fun AboutScreen(
    viewModel: AboutViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.value

    when (state) {
        is LoadingState.Loading -> {}
        is LoadingState.Loaded -> { state.data }
    }


    Card(
        backgroundColor = Color.Black,
        elevation = 0.dp,
        shape = RoundedCornerShape(5.dp),
        content = {

            Text("Hello Compose!!!", color = Color.White)
        },
    )
}
