package jp.co.soramitsu.wallet.impl.presentation.contacts

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
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
import jp.co.soramitsu.common.compose.component.FearlessProgress
import jp.co.soramitsu.common.compose.component.H5
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.ToolbarBottomSheet
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black05
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.white24
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.withNoFontPadding
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class Contact(
    val name: String,
    val address: String,
    val image: Any,
    val chainId: ChainId,
    val isSavedToContacts: Boolean
)

data class ContactsViewState(
    val contactBookAddresses: Map<String?, List<Contact>>
) {
    companion object {
        val default = ContactsViewState(emptyMap())
    }

    val isEmpty: Boolean
        get() = contactBookAddresses.isEmpty()
}

interface ContactsScreenInterface {
    fun onContactClick(contact: Contact)
    fun onNavigationClick()
    fun onCreateContactClick(chainId: ChainId? = null, address: String? = null)
}

@Composable
fun ContactsContent(
    state: LoadingState<ContactsViewState>,
    callback: ContactsScreenInterface
) {
    BottomSheetScreen {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .nestedScroll(rememberNestedScrollInteropConnection())
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                ToolbarBottomSheet(
                    title = stringResource(id = R.string.common_contacts),
                    onNavigationClick = callback::onNavigationClick
                )
                MarginVertical(margin = 24.dp)
                when {
                    state is LoadingState.Loading -> {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            FearlessProgress(
                                Modifier.align(Alignment.Center)
                            )
                        }
                    }

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
        EmptyMessage(message = R.string.contacts_empty_message, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ColumnScope.Content(state: ContactsViewState, callback: ContactsScreenInterface) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f)
    ) {
        state.contactBookAddresses.map {
            item {
                ContactGroupItem(it.key)
            }
            items(it.value) { contact ->
                ContactItem(
                    contact = contact,
                    onItemClick = callback::onContactClick,
                    onCreateContactClick = callback::onCreateContactClick
                )
            }
        }
    }
}

@Composable
fun ContactGroupItem(title: String?) {
    Box(
        modifier = Modifier.height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        H5(text = title.orEmpty())
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onItemClick: ((Contact) -> Unit)? = null,
    onCreateContactClick: ((chainId: ChainId?, address: String?) -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onItemClick?.invoke(contact)
            }
    ) {
        Icon(
            painter = rememberAsyncImagePainter(model = contact.image),
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
            val name = contact.name.ifEmpty {
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
                text = contact.address.shortenAddress(),
                color = Color.White
            )
        }
        if (!contact.isSavedToContacts) {
            BackgroundCorneredWithBorder(
                backgroundColor = black05,
                borderColor = white24,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clickable {
                        onCreateContactClick?.invoke(contact.chainId, contact.address)
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
private fun PreviewContactsContent() {
    val contactsSet = setOf(
        Contact("Address 1 name of a very long text to show how it looks in UI", "address1qasd32dqa32e32r3qqed", R.drawable.ic_plus_circle, "", true),
        Contact("" ?: "John Mir", "32dfs4323AE3asdqa32e32r3qqed", R.drawable.ic_plus_circle, "", false)
    )
    val addressBookAddresses = mapOf<String?, List<Contact>>("J" to contactsSet.toList().subList(0, 1))

    val state = LoadingState.Loaded(ContactsViewState(addressBookAddresses))
    val callback = object : ContactsScreenInterface {
        override fun onContactClick(contact: Contact) {}
        override fun onNavigationClick() {}
        override fun onCreateContactClick(chainId: ChainId?, address: String?) {}
    }
    FearlessTheme {
        ContactsContent(state, callback)
    }
}