package jp.co.soramitsu.wallet.impl.data.mappers

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * History rows are cached in the operations table and read back from it. A swap's module (shown on its details
 * screen) was written only for extrinsics, so every cached swap came back with an empty module.
 */
class OperationLocalRoundTripTest {

    private val xor = mockk<Asset>(relaxed = true) {
        every { id } returns "xor"
        every { chainId } returns "sora"
    }
    private val valAsset = mockk<Asset>(relaxed = true) {
        every { id } returns "val"
        every { chainId } returns "sora"
    }
    private val sora = mockk<Chain>(relaxed = true) { every { assets } returns listOf(xor, valAsset) }

    @Test
    fun `a cached swap keeps its module`() {
        val swap = Operation.Type.Swap(
            hash = "0x01",
            module = "liquidityProxy",
            baseAssetAmount = BigInteger.TEN,
            liquidityProviderFee = BigInteger.ONE,
            selectedMarket = "SMART",
            targetAsset = valAsset,
            targetAssetAmount = BigInteger.TWO,
            networkFee = BigInteger.ONE,
            status = Operation.Status.COMPLETED
        )

        val restored = roundTrip(swap)

        assertEquals(swap, restored)
    }

    @Test
    fun `a cached extrinsic keeps its module and call`() {
        val extrinsic = Operation.Type.Extrinsic(
            hash = "0x02",
            module = "staking",
            call = "bond",
            fee = BigInteger.ONE,
            status = Operation.Status.COMPLETED
        )

        assertEquals(extrinsic, roundTrip(extrinsic))
    }

    @Test
    fun `a transfer stores no module`() {
        val transfer = Operation.Type.Transfer(
            hash = "0x03",
            myAddress = "me",
            amount = BigInteger.ONE,
            receiver = "them",
            sender = "me",
            status = Operation.Status.COMPLETED,
            fee = BigInteger.ONE
        )

        val local = mapOperationToOperationLocalDb(operation(transfer), OperationLocal.Source.SUBQUERY)

        assertNull(local.module)
        assertNull(local.call)
    }

    private fun roundTrip(type: Operation.Type): Operation.Type {
        val local = mapOperationToOperationLocalDb(operation(type), OperationLocal.Source.SUBQUERY)
        return mapOperationLocalToOperation(local, xor, sora).type
    }

    private fun operation(type: Operation.Type) = Operation(id = "1", address = "me", type = type, time = 1L, chainAsset = xor)
}
