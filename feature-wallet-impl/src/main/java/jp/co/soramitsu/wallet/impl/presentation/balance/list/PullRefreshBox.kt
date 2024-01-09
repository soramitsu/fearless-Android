package jp.co.soramitsu.wallet.impl.presentation.balance.list

import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun PullRefreshBox(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var refreshing by remember { mutableStateOf(false) }

    val refreshScope = rememberCoroutineScope()
    fun refresh() = refreshScope.launch {
        refreshing = true
        onRefresh()
        delay(400)
        refreshing = false
    }

    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = ::refresh
    )

    Box(Modifier.pullRefresh(state)) {
        content()

        PullRefreshIndicator(
            refreshing = refreshing,
            state = state,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = colorAccentDark
        )
    }
}
