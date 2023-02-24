package jp.co.soramitsu.wallet.impl.presentation.balance.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.getImageRequest
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.wallet.impl.presentation.model.OperationModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance

@Composable
fun TransactionItem(
    item: OperationModel,
    transactionClicked: (OperationModel) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            transactionClicked(item)
        }
    ) {
        AsyncImage(
            model = when (item.assetIconUrl) {
                null -> item.operationIcon
                else -> getImageRequest(LocalContext.current, item.assetIconUrl)
            },
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterVertically)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            B1(
                text = item.header,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            B2(
                text = item.subHeader,
                textAlign = TextAlign.Start,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
        MarginHorizontal(margin = 4.dp)
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row {
                B1(
                    text = item.amount,
                    color = item.amountColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(5.dp))
                if (item.statusAppearance != OperationStatusAppearance.COMPLETED) {
                    Image(
                        res = item.statusAppearance.icon,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            B2(
                text = item.time.formatDateTime(LocalContext.current).toString(),
                textAlign = TextAlign.End,
                maxLines = 1,
                color = Color.White.copy(alpha = 0.64f)
            )
        }
    }
}

@Composable
@Preview
private fun PreviewTransactionItem() {
    TransactionItem(
        item = OperationModel(
            id = "",
            time = System.currentTimeMillis(),
            header = "Header",
            statusAppearance = OperationStatusAppearance.COMPLETED,
            amount = "amount",
            operationIcon = null,
            subHeader = "subHeader"
        ),
        transactionClicked = {}
    )
}
