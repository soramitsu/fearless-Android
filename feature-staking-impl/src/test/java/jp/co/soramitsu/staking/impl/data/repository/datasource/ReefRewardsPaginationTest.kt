package jp.co.soramitsu.staking.impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.subquery.ReefAddress
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsConnection
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsEdge
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsNode
import jp.co.soramitsu.common.data.network.subquery.ReefStakingRewardsResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidPageInfo
import jp.co.soramitsu.staking.impl.data.network.subquery.request.ReefStakingRewardsRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ReefRewardsPaginationTest {
    @Test
    fun `valid pages sum once and quote opaque provider cursors`() = runTest {
        val cursor = "cursor\"}) { injected } #\n\\"
        val seen = mutableListOf<String?>()
        val total = fetchReefRewardsTotal {
            seen.add(it)
            if (it == null) page("first", true, cursor) else page("last", false, "end")
        }
        assertEquals(BigInteger.TWO, total)
        assertEquals(listOf(null, cursor), seen)
        assertTrue(ReefStakingRewardsRequest("address", offset = cursor).query.contains("after: ${Gson().toJson(cursor)}"))
        assertTrue(runCatching { ReefStakingRewardsRequest("address", offset = "a".repeat(4097)) }.isFailure)
    }

    @Test
    fun `repeated cursors empty continuation pages and oversized cursors fail closed`() = runTest {
        var calls = 0
        val result = runCatching {
            fetchReefRewardsTotal {
                calls++
                page("id$calls", true, "same")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(2, calls)
        assertTrue(runCatching { fetchReefRewardsTotal { page("", true, "next", empty = true) } }.isFailure)
        assertTrue(runCatching { fetchReefRewardsTotal { page("id", true, "a".repeat(4097)) } }.isFailure)
        assertTrue(runCatching { fetchReefRewardsTotal { page("duplicate", true, "next") } }.isFailure)
    }

    @Test
    fun `unique malicious pages still stop at the page limit and stalled requests time out`() = runTest {
        var calls = 0
        val result = runCatching {
            fetchReefRewardsTotal {
                calls++
                page("id$calls", true, "next$calls")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(1000, calls)
        assertTrue(runCatching { fetchReefRewardsTotal { awaitCancellation() } }.isFailure)
    }

    private fun page(
        id: String,
        hasNext: Boolean,
        cursor: String,
        empty: Boolean = false
    ) = ReefStakingRewardsResponse(
        ReefRewardsConnection(
            if (empty) emptyList() else listOf(ReefRewardsEdge(ReefRewardsNode(id, "Reward", BigInteger.ONE, "0", ReefAddress("address")))),
            SubsquidPageInfo(hasNext, cursor)
        )
    )
}
