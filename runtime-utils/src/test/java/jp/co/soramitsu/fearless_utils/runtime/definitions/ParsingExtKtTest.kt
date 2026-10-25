package jp.co.soramitsu.fearless_utils.runtime.definitions

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ParsingExtKtTest {

    @Test
    fun `should split one-depth tuple`() {
        val splitted = "(ParaId, CollatorId, A)".splitTuple()

        assertEquals(listOf("ParaId", "CollatorId", "A"), splitted)
    }

    @Test
    fun `should split with nested commas`() {
        val splitted = "(BalanceOf<T, I>, BidKind<AccountId, BalanceOf<T, I>>)".splitTuple()

        assertEquals(listOf("BalanceOf<T,I>", "BidKind<AccountId,BalanceOf<T,I>>"), splitted)
    }

    @Test
    fun `should split with nested array`() {
        val splitted = "(NominatorIndex, [CompactScore; 0], ValidatorIndex)".splitTuple()

        assertEquals(
            listOf("NominatorIndex", "[CompactScore;0]", "ValidatorIndex"),
            splitted
        )
    }

    @Test
    fun `should split with nested tuple`() {
        val splitted = "(ParaId, Option<(CollatorId, Retriable)>)".splitTuple()

        assertEquals(listOf("ParaId", "Option<(CollatorId,Retriable)>"), splitted)
    }

    @Test
    fun `spaces at the end (from Karura metadata)`() {
        val splitted = "(ParaId, InboundStatus, Vec<(RelayBlockNumber, XcmpMessageFormat)>,)    ".splitTuple()

        assertEquals(listOf("ParaId", "InboundStatus", "Vec<(RelayBlockNumber,XcmpMessageFormat)>"), splitted)
    }

    @Test
    fun `newline at the end (from Karura metadata)`() {
        val splitted = "(ParaId,InboundStatus,Vec<(RelayBlockNumber,XcmpMessageFormat)>,)\n".splitTuple()

        assertEquals(listOf("ParaId", "InboundStatus", "Vec<(RelayBlockNumber,XcmpMessageFormat)>"), splitted)
    }
}