package science.wasabi.wear.components

/**
 * Created by benja on 7/8/2017.
 */
data class ClockState(
    val centerX: Float,
    val centerY: Float,
    val isMuteMode: Boolean,
    val isAmbient: Boolean,
    val isLowBitAmbient: Boolean,
    val isBurnInProtection: Boolean
)