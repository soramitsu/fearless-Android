package jp.co.soramitsu.common.launch

import android.os.Build
import android.view.View
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import java.lang.ref.WeakReference
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier

/**
 * The first screen's tiers during the launch flight and a later arrival (brand kit SPEC v4 part 1,
 * "The wallet flies up" and "Later arrivals"). A screen declares which of its parts belong to which
 * tier ([launchTier] in Compose, [registerView] for views); the launch overlay or the unlock landing
 * then writes each tier's scale and opacity here once per frame, about one focus point in window
 * coordinates. Only graphics-layer properties change: no layout, no recomposition of the screen
 * (Compose reads the values inside `graphicsLayer {}`, which redraws the layer only).
 *
 * When nothing drives it, every tier is at scale 1 and fully opaque, and no transform is left on
 * any view ([end] resets them and their pivots).
 */
object LaunchFlightStage {
    /** Tiers 0 (nearest) to 5, as in the kit's `flight.samples.tiers`. */
    const val TIERS = 6

    private val activeState = mutableStateOf(false)
    private val scales = Array(TIERS) { mutableFloatStateOf(1f) }
    private val alphas = Array(TIERS) { mutableFloatStateOf(1f) }
    private val focusX = mutableFloatStateOf(0f)
    private val focusY = mutableFloatStateOf(0f)
    private class Registered(val view: WeakReference<View>, val tier: Int, val group: String)

    private val views = mutableListOf<Registered>()
    private var drivenGroup: String? = null
    private val location = IntArray(2)

    /** How many composed parts and attached views currently declare a tier. */
    @Volatile
    var declared: Int = 0
        private set

    /** True while a flight or a landing drives the tiers. */
    val active: Boolean get() = activeState.value

    /** Starts driving the tiers about ([x], [y]) in window pixels, every tier at ([scale], [alpha]) of its own. */
    fun begin(x: Float, y: Float, scale: (Int) -> Float, alpha: (Int) -> Float, group: String? = null) {
        focusX.floatValue = x
        focusY.floatValue = y
        drivenGroup = group
        apply(scale, alpha)
        activeState.value = true
    }

    /** One frame: each tier's scale and opacity. */
    fun apply(scale: (Int) -> Float, alpha: (Int) -> Float) {
        for (k in 0 until TIERS) {
            scales[k].floatValue = scale(k)
            alphas[k].floatValue = alpha(k)
        }
        val iterator = views.iterator()
        while (iterator.hasNext()) {
            val registered = iterator.next()
            val view = registered.view.get()
            if (view == null) {
                iterator.remove()
                continue
            }
            if (drives(registered.group)) placeView(view, registered.tier)
        }
    }

    /** The flight or landing is over: every tier at rest, transforms and pivots cleared. */
    fun end() {
        for (k in 0 until TIERS) {
            scales[k].floatValue = 1f
            alphas[k].floatValue = 1f
        }
        activeState.value = false
        drivenGroup = null
        views.mapNotNull { it.view.get() }.forEach(::clearView)
    }

    /** A landing drives only its own screen's views (the PIN page leaving underneath is left alone). */
    private fun drives(group: String): Boolean = drivenGroup == null || drivenGroup == group

    fun scaleOf(tier: Int): Float = scales[tier.coerceIn(0, TIERS - 1)].floatValue
    fun alphaOf(tier: Int): Float = alphas[tier.coerceIn(0, TIERS - 1)].floatValue

    /** Group of the wallet's own screens (the main screen and its tab bar), driven by the unlock landing. */
    const val MAIN_GROUP = "main"

    /** Declares [view] as part of [tier] of the screen [group] while it is attached. */
    fun registerView(view: View, tier: Int, group: String = "") {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (views.none { it.view.get() === v }) {
                    views += Registered(WeakReference(v), tier.coerceIn(0, TIERS - 1), group)
                    declared++
                    if (activeState.value && drives(group)) placeView(v, tier)
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                if (views.removeAll { it.view.get() === v || it.view.get() == null }) declared = (declared - 1).coerceAtLeast(0)
                clearView(v)
            }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
    }

    /** How many composed parts declare a tier (views not counted). */
    @Volatile
    var composedTiers: Int = 0
        private set

    internal fun declare(delta: Int) {
        declared = (declared + delta).coerceAtLeast(0)
        composedTiers = (composedTiers + delta).coerceAtLeast(0)
    }

    private fun placeView(view: View, tier: Int) {
        val parent = view.parent as? View
        if (parent != null) {
            parent.getLocationInWindow(location)
            view.pivotX = focusX.floatValue - (location[0] + view.left - parent.scrollX)
            view.pivotY = focusY.floatValue - (location[1] + view.top - parent.scrollY)
        }
        val s = scaleOf(tier)
        view.scaleX = s
        view.scaleY = s
        view.alpha = alphaOf(tier)
    }

    private fun clearView(view: View) {
        view.scaleX = 1f
        view.scaleY = 1f
        view.alpha = 1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.resetPivot() else {
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
        }
    }

    /** For tests: nothing declared, nothing driven. */
    fun resetForTest() {
        end()
        views.clear()
        declared = 0
        composedTiers = 0
    }

    /** Where the focus is, in window pixels (read inside graphics layers). */
    internal fun focusX(): Float = focusX.floatValue
    internal fun focusY(): Float = focusY.floatValue
}

/**
 * Declares this part of the screen as launch tier [tier] (SPEC v4 part 1, "Tiers"): wallet home 0 the
 * balance card, 1 the round actions, 2 the header and the tab bar, 3 the tabs or segment control, 4 the
 * list; PIN, unlock or welcome 0 the mark, 1 the main control, 2 the rest. While a flight or a landing
 * runs, the part is scaled about the focus point and faded; otherwise it is left exactly as it is.
 */
fun Modifier.launchTier(tier: Int): Modifier = composed {
    DisposableEffect(Unit) {
        LaunchFlightStage.declare(+1)
        onDispose { LaunchFlightStage.declare(-1) }
    }
    if (!LaunchFlightStage.active) return@composed this
    val origin = remember { FloatArray(2) }
    this
        .onPlaced { coordinates ->
            val at = coordinates.positionInWindow()
            origin[0] = at.x
            origin[1] = at.y
        }
        .graphicsLayer {
            val s = LaunchFlightStage.scaleOf(tier)
            scaleX = s
            scaleY = s
            alpha = LaunchFlightStage.alphaOf(tier)
            val w = size.width
            val h = size.height
            if (w > 0f && h > 0f) {
                transformOrigin = TransformOrigin((LaunchFlightStage.focusX() - origin[0]) / w, (LaunchFlightStage.focusY() - origin[1]) / h)
            }
        }
}

/**
 * Whether the launch clip, its flight and the unlock landing may move (SPEC v4, "Reduced motion"): not under
 * reduced motion (animator scale 0) nor under the app's own Reduced effects choice. High contrast alone (which
 * also selects the Reduced paint tier) keeps the launch, as kit v3 did.
 */
fun launchMotionAllowed(animatorsEnabled: Boolean, effects: FwEffects): Boolean =
    animatorsEnabled && !effects.reduceMotion && (effects.tier != FwTier.Reduced || effects.highContrast)
