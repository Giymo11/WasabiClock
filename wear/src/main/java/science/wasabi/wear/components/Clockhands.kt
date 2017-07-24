package science.wasabi.wear.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint


data class ShadowLayer(val radius: Float, val dx: Float, val dy: Float, val color: Int)

/**
 * Created by benja on 7/8/2017.
 */
open class ClockDrawable(val color: Int, val strokeWidth: Float, val shadowLayer: ShadowLayer) {

    val activePaint: Paint by lazy {
        val paint = Paint()
        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.strokeCap = Paint.Cap.BUTT
        paint.isAntiAlias = true
        //paint.alpha = if(mMuteMode) 100 else 255
        paint.setShadowLayer(shadowLayer.radius, shadowLayer.dx, shadowLayer.dy, shadowLayer.color)
        paint
    }
    val ambientPaint: Paint by lazy {
        val paint = Paint(activePaint)
        paint.color = Color.WHITE
        //paint.alpha = if(mMuteMode) 100 else 255
        paint.isAntiAlias = false
        paint.clearShadowLayer()
        paint
    }
    protected val backgroundPaint: Paint by lazy {
        val paint = Paint(activePaint)
        paint.color = Color.BLACK
        paint.strokeWidth = strokeWidth + 0.5f
        paint.clearShadowLayer()
        paint
    }
    fun paint(ambient: Boolean): Paint = if(!ambient) activePaint else ambientPaint
    open fun withColor(newColor: Int): ClockDrawable { return ClockDrawable(newColor, strokeWidth, shadowLayer) }
    /**
     * Draws the hand onto the canvas in the 12-o-clock position, so rotate beforehand.
     */
    open fun drawOntoCanvas(canvas: Canvas, center: Float, ambient: Boolean) {
        canvas.drawLine(center, center - 0f, center, center - (center * 0.5).toFloat(), backgroundPaint)
        canvas.drawLine(center, center - 0f, center, center - (center * 0.5).toFloat(), paint(ambient))
    }
}

class AnalogHand(color: Int, strokeWidth: Float, shadowLayer: ShadowLayer, val startScale: Float, val endScale: Float) : ClockDrawable(color, strokeWidth, shadowLayer) {
    override fun withColor(newColor: Int): ClockDrawable { return AnalogHand(newColor, strokeWidth, shadowLayer, startScale, endScale) }
    override fun drawOntoCanvas(canvas: Canvas, center: Float, ambient: Boolean) {
        canvas.drawLine(center, center - (center * startScale), center, center - (center * endScale), backgroundPaint)
        canvas.drawLine(center, center - (center * startScale), center, center - (center * endScale), paint(ambient))
    }
}

class Ticks(color: Int, strokeWidth: Float, shadowLayer: ShadowLayer, val startScale: Float, val endScale: Float) : ClockDrawable(color, strokeWidth, shadowLayer) {
    override fun withColor(newColor: Int): ClockDrawable { return Ticks(newColor, strokeWidth, shadowLayer, startScale, endScale) }
    override fun drawOntoCanvas(canvas: Canvas, center: Float, ambient: Boolean) {
        val centerX = center
        val centerY = center
        val innerTickRadius = center * startScale
        val outerTickRadius = center * endScale
        for (tickIndex in 0..11) {
            val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
            val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
            val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
            val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
            val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
            canvas.drawLine(centerX + innerX, centerY + innerY,
                    centerX + outerX, centerY + outerY, paint(ambient))
        }
    }
}

class HandSet(val hourHand: ClockDrawable, val minuteHand: ClockDrawable, val secondHand: ClockDrawable, val background: Bitmap?)