package science.wasabi.wear.minimal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v7.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

data class ShadowLayer(val radius: Float, val dx: Float, val dy: Float, val color: Int)


/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
class WasabiMinimal : CanvasWatchFaceService() {

    override fun onCreateEngine(): CanvasWatchFaceService.Engine {
        return Engine()
    }

    private class EngineHandler(reference: WasabiMinimal.Engine) : Handler() {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()

            when (msg.what) {
                MSG_UPDATE_TIME -> engine?.handleUpdateTimeMessage()
                else -> {}
            }
        }
    }

    private inner class Engine : CanvasWatchFaceService.Engine() {

        private val CENTER_GAP_AND_CIRCLE_RADIUS = 4f
        private val SHADOW_RADIUS = 6

        private val mPeekCardBounds = Rect()
        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)
        private var mCalendar: Calendar? = null
        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar!!.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }
        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0.toFloat()
        private var mCenterY: Float = 0.toFloat()

        /* Set defaults for colors */
        private val defaultWatchHandColor = Color.WHITE
        private val defaultWatchHandHighlightColor = Color.RED
        private val defaultWatchHandShadowColor = Color.BLACK

        private val HOUR_STROKE_WIDTH = 5f
        private val MINUTE_STROKE_WIDTH = 3f
        private val SECOND_STROKE_WIDTH = 7f

        val shadowLayer = ShadowLayer(SHADOW_RADIUS.toFloat(), 0f, 0f, defaultWatchHandShadowColor)
        private var hourHand: ClockHand = AnalogHand(defaultWatchHandColor, HOUR_STROKE_WIDTH, shadowLayer, 255, 0.0f, 0.33f)
        private var minuteHand: ClockHand = AnalogHand(defaultWatchHandColor, MINUTE_STROKE_WIDTH, shadowLayer, 255, 0.33f, 0.9f)
        private var secondHand: ClockHand = AnalogHand(defaultWatchHandHighlightColor, SECOND_STROKE_WIDTH, shadowLayer, 255, 0.8f, 1.0f)

        private var mBackgroundPaint: Paint? = null
        private var mBackgroundBitmap: Bitmap? = null
        private var mGrayBackgroundBitmap: Bitmap? = null
        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        open inner class ClockHand(val color: Int, val strokeWidth: Float, val shadowLayer: ShadowLayer, val alpha: Int = 255) {
            val activePaint: Paint by lazy {
                val paint = Paint()
                paint.color = color
                paint.strokeWidth = strokeWidth
                paint.isAntiAlias = true
                paint.setShadowLayer(shadowLayer.radius, shadowLayer.dx, shadowLayer.dy, shadowLayer.color)
                paint
            }
            val ambientPaint: Paint by lazy {
                val paint = Paint(activePaint)
                paint.color = Color.WHITE
                paint.isAntiAlias = false
                paint.clearShadowLayer()
                paint
            }
            fun paint(): Paint = if(!mAmbient) activePaint else ambientPaint
            open fun withColor(newColor: Int): ClockHand { return ClockHand(newColor, strokeWidth, shadowLayer, alpha) }
            open fun withAlpha(newAlpha: Int): ClockHand { return ClockHand(color, strokeWidth, shadowLayer, newAlpha) }
            /**
             * Draws the hand onto the canvas in the 12-o-clock position, so rotate beforehand.
             */
            open fun drawOntoCanvas(canvas: Canvas, center: Float) {
                canvas.drawLine(center, center - CENTER_GAP_AND_CIRCLE_RADIUS, center, center - (center * 0.5).toFloat(), paint())
            }
        }

        inner class AnalogHand(color: Int, strokeWidth: Float, shadowLayer: ShadowLayer, alpha: Int, val startScale: Float, val endScale: Float) : ClockHand(color, strokeWidth, shadowLayer, alpha) {
            override fun withColor(newColor: Int): ClockHand { return AnalogHand(newColor, strokeWidth, shadowLayer, alpha, startScale, endScale) }
            override fun withAlpha(newAlpha: Int): ClockHand { return AnalogHand(color, strokeWidth, shadowLayer, newAlpha, startScale, endScale) }
            override fun drawOntoCanvas(canvas: Canvas, center: Float) {
                canvas.drawLine(center, center - (center * startScale), center, center - (center * endScale), paint())
            }
        }

        override fun onCreate(holder: SurfaceHolder?) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@WasabiMinimal)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build())

            mBackgroundPaint = Paint()
            mBackgroundPaint!!.color = Color.BLACK
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_shinobu)

            /* Extract colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap!!).generate { palette ->
                if (palette != null) {
                    hourHand = hourHand.withColor(palette.getLightVibrantColor(defaultWatchHandColor))
                    minuteHand = minuteHand.withColor(palette.getLightVibrantColor(defaultWatchHandColor))
                    secondHand = secondHand.withColor(palette.getLightVibrantColor(defaultWatchHandHighlightColor))
                    updateWatchHandStyle()
                }
            }

            mCalendar = Calendar.getInstance()
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties?.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false) ?: false
            mBurnInProtection = properties?.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false) ?: false
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        fun updateWatchHandStyle() {}

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                hourHand = hourHand.withAlpha(if (inMuteMode) 100 else 255)
                minuteHand = minuteHand.withAlpha(if (inMuteMode) 100 else 255)
                secondHand = secondHand.withAlpha(if (inMuteMode) 80 else 255)
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap!!.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (mBackgroundBitmap!!.width * scale).toInt(),
                    (mBackgroundBitmap!!.height * scale).toInt(), true)

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap!!.width,
                    mBackgroundBitmap!!.height,
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mGrayBackgroundBitmap!!)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap!!, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                // The user has started touching the screen.
                WatchFaceService.TAP_TYPE_TOUCH -> {}
                // The user has started a different gesture or otherwise cancelled the tap.
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {}
                // The user has completed the tap gesture.
                WatchFaceService.TAP_TYPE_TAP -> Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT).show()
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            val now = System.currentTimeMillis()
            mCalendar!!.timeInMillis = now

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas!!.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas!!.drawBitmap(mGrayBackgroundBitmap!!, 0f, 0f, mBackgroundPaint)
            } else {
                canvas!!.drawBitmap(mBackgroundBitmap!!, 0f, 0f, mBackgroundPaint)
            }

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            fun drawTicks(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint) {
                val innerTickRadius = centerX - 16
                val outerTickRadius = centerX
                for (tickIndex in 0..11) {
                    val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                    val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
                    val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
                    val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
                    val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
                    canvas.drawLine(centerX + innerX, centerY + innerY,
                            centerX + outerX, centerY + outerY, paint)
                }
            }
            drawTicks(canvas, mCenterX, mCenterY, minuteHand.paint())

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds = mCalendar!!.get(Calendar.SECOND) + mCalendar!!.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar!!.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar!!.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar!!.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            hourHand.drawOntoCanvas(canvas, mCenterX)

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            minuteHand.drawOntoCanvas(canvas, mCenterX)

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                secondHand.drawOntoCanvas(canvas, mCenterX)

            }
            /* Draws the cute small circle in the middle
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint!!)*/

            /* Restore the canvas' original orientation. */
            canvas.restore()

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint!!)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar!!.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        override fun onPeekCardPositionUpdate(rect: Rect?) {
            super.onPeekCardPositionUpdate(rect)
            mPeekCardBounds.set(rect)
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@WasabiMinimal.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WasabiMinimal.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }

    companion object {
        /*
         * Update rate in milliseconds for interactive mode. We update once a second to advance the
         * second hand.
         */
        private val INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1)

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private val MSG_UPDATE_TIME = 0
    }
}