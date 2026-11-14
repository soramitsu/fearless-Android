package jp.co.soramitsu.staking.impl.domain.payout

import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.assethub.AssetHubStaking
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * A payout review names the network it was made on. When the staking selection moves to another network before the
 * payout is sent, the payout fails with a result the confirmation shows; nothing is signed and nothing is thrown.
 */
class PayoutInteractorTest {
    private val origin = ByteArray(32) { 4 }.toAddress(0)

    private fun chain(id: String) = mock<Chain> {
        on { this.id } doReturn id
        on { ecosystem } doReturn Ecosystem.Substrate
    }

    private fun review(chainId: String): MakePayoutPayload {
        val config = mock<CoreAsset> {
            on { this.chainId } doReturn chainId
            on { id } doReturn "native"
            on { precision } doReturn 10
        }
        val token = Asset.createEmpty(config, 7, ByteArray(32) { 4 }, minSupportedVersion = null).token
        return MakePayoutPayload(
            origin, BigDecimal.ONE, BigDecimal.TEN, token,
            listOf(MakePayoutPayload.PayoutStakersPayload(BigInteger.TEN, ByteArray(32) { 2 }.toAddress(0), BigInteger.ZERO))
        )
    }

    @Test
    fun `a review of another network fails without signing`() = runTest {
        val selected = chain(AssetHubStaking.KUSAMA)
        val shared = mock<StakingSharedState> { onBlocking { chain() } doReturn selected }
        val extrinsics = mock<ExtrinsicService>()
        val result = PayoutInteractor(shared, extrinsics).makePayouts(review(AssetHubStaking.POLKADOT))
        assertEquals(PayoutInteractor.STALE_REVIEW, result.exceptionOrNull()?.message)
        verifyNoInteractions(extrinsics)
    }

    @Test
    fun `a review of the selected network is submitted`() = runTest {
        val selected = chain(AssetHubStaking.POLKADOT)
        val shared = mock<StakingSharedState> { onBlocking { chain() } doReturn selected }
        val extrinsics = mock<ExtrinsicService> {
            onBlocking { submitExtrinsic(any(), any(), any(), anyOrNull(), anyOrNull(), any()) } doReturn Result.success("0x01")
        }
        val result = PayoutInteractor(shared, extrinsics).makePayouts(review(AssetHubStaking.POLKADOT))
        assertEquals("0x01", result.getOrNull())
    }
}
