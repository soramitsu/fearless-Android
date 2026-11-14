package jp.co.soramitsu.common.compose.theme.fw

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Motion constants used by the Android code equal design/fw.tokens.json motion.event. */
class FwMotionTokensTest {
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }

    @Test fun motionDurationsMatchTheSharedTokens() {
        val events = JsonParser.parseString(File(root, "design/fw.tokens.json").readText()).asJsonObject
            .getAsJsonObject("motion").getAsJsonObject("event")
        fun ms(name: String): Int {
            val value = events.getAsJsonObject(name).getAsJsonObject("\$value")
            assertEquals("ms", value.get("unit").asString)
            return value.get("value").asInt
        }
        val expected = mapOf(
            "pressIn" to FwMotionDurations.PRESS_IN,
            "pressOut" to FwMotionDurations.PRESS_OUT,
            "balanceRoll" to FwMotionDurations.BALANCE_ROLL,
            "priceFlash" to FwMotionDurations.PRICE_FLASH,
            "syncTurn" to FwMotionDurations.SYNC_TURN,
            "syncBloom" to FwMotionDurations.SYNC_BLOOM,
            "syncFail" to FwMotionDurations.SYNC_FAIL,
            "listStagger" to FwMotionDurations.LIST_STAGGER,
            "listRise" to FwMotionDurations.LIST_RISE,
            "tabWell" to FwMotionDurations.TAB_WELL,
            "txBreathe" to FwMotionDurations.TX_BREATHE,
            "txSuccess" to FwMotionDurations.TX_SUCCESS,
            "shake" to FwMotionDurations.SHAKE,
            "pinDot" to FwMotionDurations.PIN_DOT,
            "copied" to FwMotionDurations.COPIED,
            "reducedCrossfade" to FwMotionDurations.REDUCED_CROSSFADE
        )
        assertEquals(expected.keys, events.keySet().filterNot { it.startsWith("$") }.toSet())
        for ((name, value) in expected) assertEquals(name, ms(name), value)
    }

    @Test fun radiiMatchTheSharedTokens() {
        val radius = JsonParser.parseString(File(root, "design/fw.tokens.json").readText()).asJsonObject
            .getAsJsonObject("dimension").getAsJsonObject("radius")
        fun px(name: String) = radius.getAsJsonObject(name).getAsJsonObject("\$value").get("value").asFloat
        assertEquals(px("card"), FwShape.CARD.value, 0f)
        assertEquals(px("button"), FwShape.BUTTON.value, 0f)
        assertEquals(px("hero"), FwShape.HERO.value, 0f)
        assertEquals(px("well"), FwShape.WELL.value, 0f)
    }
}
