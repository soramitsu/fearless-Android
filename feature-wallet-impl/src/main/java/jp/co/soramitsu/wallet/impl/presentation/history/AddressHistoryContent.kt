package jp.co.soramitsu.wallet.impl.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCorneredWithBorder
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.withNoFontPadding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class Address(
    val name: String,
    val address: String,
    val image: Any,
    val chainId: ChainId,
    val isSavedToContacts: Boolean
)

data class AddressHistoryViewState(
    val recentAddresses: Set<Address>,
    val addressBookAddresses: Map<String?, List<Address>>
) {
    companion object {
        val default = AddressHistoryViewState(emptySet(), emptyMap())
    }

    val isEmpty: Boolean
        get() = recentAddresses.isEmpty() && addressBookAddresses.isEmpty()
}

interface AddressHistoryScreenInterface {
    fun onAddressClick(address: Address)
    fun onNavigationClick()
    fun onCreateContactClick(chainId: ChainId? = null, address: String? = null)
}

@Composable
fun AddressHistoryContent(
    state: LoadingState<AddressHistoryViewState>,
    callback: AddressHistoryScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.common_history),
                    onNavigationClicked = callback::onNavigationClick
                )
                MarginVertical(margin = 24.dp)

                when {
                    state is LoadingState.Loaded && state.data.isEmpty -> {
                        EmptyState()
                    }
                    state is LoadingState.Loaded && state.data.isEmpty.not() -> {
                        Content(state = state.data, callback = callback)
                    }
                }
                MarginVertical(margin = 12.dp)
                AccentButton(
                    text = stringResource(id = R.string.create_contact),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    onClick = callback::onCreateContactClick
                )
                MarginVertical(margin = 12.dp)
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        EmptyMessage(message = R.string.address_history_empty_message, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ColumnScope.Content(state: AddressHistoryViewState, callback: AddressHistoryScreenInterface) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f)
    ) {
        if (state.recentAddresses.isNotEmpty()) {
            item {
                AddressGroupItem(stringResource(id = R.string.recent))
            }
            items(state.recentAddresses.toList()) { address ->
                AddressItem(
                    address = address,
                    onItemClick = callback::onAddressClick,
                    onCreateContactClick = callback::onCreateContactClick
                )
            }
        }
        state.addressBookAddresses.map {
            item {
                AddressGroupItem(it.key)
            }
            items(it.value) { address ->
                AddressItem(
                    address = address,
                    onItemClick = callback::onAddressClick,
                    onCreateContactClick = callback::onCreateContactClick
                )
            }
        }
    }
}

@Composable
fun AddressGroupItem(title: String?) {
    Box(
        modifier = Modifier.height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        H5(text = title.orEmpty())
    }
}

@Composable
fun AddressItem(
    address: Address,
    onItemClick: ((Address) -> Unit)? = null,
    onCreateContactClick: ((chainId: ChainId?, address: String?) -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onItemClick?.invoke(address)
            }
    ) {
        Icon(
            painter = rememberAsyncImagePainter(model = address.image),
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.CenterVertically)
        )
        MarginHorizontal(margin = 8.dp)
        Column(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .weight(1f)
        ) {
            val name = address.name.ifEmpty {
                stringResource(id = R.string.common_unknown)
            }
            H5(
                text = name.withNoFontPadding(),
                color = black2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MarginVertical(margin = 4.dp)
            B1(
                text = stringResource(
                    id = R.string.common_middle_dots,
                    address.address.take(10),
                    address.address.takeLast(10)
                ),
                color = Color.White
            )
        }
        if (!address.isSavedToContacts) {
            BackgroundCorneredWithBorder(
                backgroundColor = black05,
                borderColor = white24,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clickable {
                        onCreateContactClick?.invoke(address.chainId, address.address)
                    }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_recipient),
                    tint = Color.White,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
@Preview
fun PreviewAddressHistoryContent() {
    val addressSet = setOf(
        Address("Address 1 name of a very long text to show how it looks in UI", "address1qasd32dqa32e32r3qqed", R.drawable.ic_plus_circle, "", true),
        Address("" ?: "John Mir", "32dfs4323AE3asdqa32e32r3qqed", R.drawable.ic_plus_circle, "", false)
    )
    val addressBookAddresses = mapOf<String?, List<Address>>("J" to addressSet.toList().subList(0, 1))

    val state = LoadingState.Loaded(AddressHistoryViewState(addressSet, addressBookAddresses))
    val callback = object : AddressHistoryScreenInterface {
        override fun onAddressClick(address: Address) {}
        override fun onNavigationClick() {}
        override fun onCreateContactClick(chainId: ChainId?, address: String?) {}
    }
    FearlessTheme {
        AddressHistoryContent(state, callback)
    }
}
