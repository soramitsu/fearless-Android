package jp.co.soramitsu.wallet.impl.presentation.manageassets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Two assets of one chain can share a canonical key; a repeated list key crashes the LazyColumn (4.3.1, 4.3.2). */
class ManageAssetListKeyTest {
    private fun row(id: String, canonicalKey: String, chainId: String = "acala") = ManageAssetItemState(
        canonicalAssetKey = canonicalKey, id = id, imageUrl = null, chainName = "Acala", assetName = null, symbol = id,
        amount = "0", fiatAmount = null, chainId = chainId, isChecked = true, showEdit = false, isZeroAmount = true
    )

    @Test fun assetsSharingACanonicalKeyGetDifferentListKeys() {
        val tdot = row("tdot-uuid", "substrate:acala:0")
        val glmr = row("glmr-uuid", "substrate:acala:0")
        assertEquals(tdot.canonicalAssetKey, glmr.canonicalAssetKey)
        assertNotEquals(tdot.listKey, glmr.listKey)
    }

    @Test fun theSameRegistryIdOnAnotherChainIsAnotherRow() {
        assertNotEquals(row("same", "a", chainId = "acala").listKey, row("same", "a", chainId = "karura").listKey)
    }
}
