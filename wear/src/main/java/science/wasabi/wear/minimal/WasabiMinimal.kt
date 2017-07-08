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

        open inner class ClockHand(val color: Int, val strokeWidth: Float, val shadowLayer: ShadowLayer) {

            val activePaint: Paint by lazy {
                val paint = Paint()
                paint.color = color
                paint.strokeWidth = strokeWidth
                paint.strokeCap = Paint.Cap.ROUND
                paint.isAntiAlias = true
                paint.alpha = if(mMuteMode) 100 else 255
                paint.setShadowLayer(shadowLayer.radius, shadowLayer.dx, shadowLayer.dy, shadowLayer.color)
                paint
            }
            val ambientPaint: Paint by lazy {
                val paint = Paint(activePaint)
                paint.color = Color.WHITE
                paint.alpha = if(mMuteMode) 100 else 255
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
            fun paint(): Paint = if(!mAmbient) activePaint else ambientPaint
            open fun withColor(newColor: Int): ClockHand { return ClockHand(newColor, strokeWidth, shadowLayer) }
            /**
             * Draws the hand onto the canvas in the 12-o-clock position, so rotate beforehand.
             */
            open fun drawOntoCanvas(canvas: Canvas, center: Float) {
                canvas.drawLine(center, center - 0f, center, center - (center * 0.5).toFloat(), backgroundPaint)
                canvas.drawLine(center, center - 0f, center, center - (center * 0.5).toFloat(), paint())
            }
        }

        inner class AnalogHand(color: Int, strokeWidth: Float, shadowLayer: ShadowLayer, val startScale: Float, val endScale: Float) : ClockHand(color, strokeWidth, shadowLayer) {
            override fun withColor(newColor: Int): ClockHand { return AnalogHand(newColor, strokeWidth, shadowLayer, startScale, endScale) }
            override fun drawOntoCanvas(canvas: Canvas, center: Float) {
                canvas.drawLine(center, center - (center * startScale), center, center - (center * endScale), backgroundPaint)
                canvas.drawLine(center, center - (center * startScale), center, center - (center * endScale), paint())
            }
        }

        inner class HandSet(val hourHand: ClockHand, val minuteHand: ClockHand, val secondHand: ClockHand, val background: Bitmap?)

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

        private val shinobuYellow = Color.parseColor("#EFE05F")
        private val makiRed = Color.parseColor("#d84e60")
        private val nepuPurple = Color.parseColor("#F1C8F1")

        /* Set defaults for colors */
        private val defaultWatchHandShadowColor = Color.BLACK

        private val HOUR_STROKE_WIDTH = 13f
        private val MINUTE_STROKE_WIDTH = 9f
        private val SECOND_STROKE_WIDTH = 24f

        private val SHADOW_RADIUS = 7
        val shadowLayer = ShadowLayer(SHADOW_RADIUS.toFloat(), 0f, 0f, defaultWatchHandShadowColor)
        val shinobuSet = HandSet(
                hourHand = AnalogHand(Color.parseColor("#EEEEEE"), HOUR_STROKE_WIDTH, shadowLayer, 0.2f, 0.5f),
                minuteHand = AnalogHand(Color.parseColor("#e42e40"), MINUTE_STROKE_WIDTH, shadowLayer, 0.35f, 0.90f),
                secondHand = AnalogHand(Color.parseColor("#222222"), SECOND_STROKE_WIDTH, shadowLayer, 0.95f, 1.0f),
                background = BitmapFactory.decodeResource(resources, R.drawable.bg_shinobu)
            )

        val reinaSet = HandSet(
                hourHand = AnalogHand(Color.parseColor("#42688d"), HOUR_STROKE_WIDTH, shadowLayer, 0.2f, 0.5f),
                minuteHand = AnalogHand(Color.parseColor("#fffbbc"), MINUTE_STROKE_WIDTH, shadowLayer, 0.35f, 0.90f),
                secondHand = AnalogHand(Color.parseColor("#572b46"), SECOND_STROKE_WIDTH, shadowLayer, 0.95f, 1.0f),
                background = BitmapFactory.decodeResource(resources, R.drawable.bg_reina)
        )
        val bluesnowSet = HandSet(
                hourHand = AnalogHand(Color.parseColor("#393846"), HOUR_STROKE_WIDTH, shadowLayer, 0.2f, 0.5f),
                minuteHand = AnalogHand(Color.parseColor("#3d4cb3"), MINUTE_STROKE_WIDTH, shadowLayer, 0.35f, 0.90f),
                secondHand = AnalogHand(Color.parseColor("#f9f9f7"), SECOND_STROKE_WIDTH, shadowLayer, 0.95f, 1.0f),
                background = BitmapFactory.decodeResource(resources, R.drawable.bg_bluesnow)
        )
        val kumikoSet = HandSet(
                hourHand = AnalogHand(Color.parseColor("#3d2221"), HOUR_STROKE_WIDTH, shadowLayer, 0.2f, 0.5f),
                minuteHand = AnalogHand(Color.parseColor("#934e2d"), MINUTE_STROKE_WIDTH, shadowLayer, 0.35f, 0.90f),
                secondHand = AnalogHand(Color.parseColor("#eae4da"), SECOND_STROKE_WIDTH, shadowLayer, 0.95f, 1.0f),
                background = BitmapFactory.decodeResource(resources, R.drawable.bg_kumiko)
        )
        val yumeSet = HandSet(
                hourHand = AnalogHand(Color.parseColor("#283d50"), HOUR_STROKE_WIDTH, shadowLayer, 0.2f, 0.5f),
                minuteHand = AnalogHand(Color.parseColor("#a55c65"), MINUTE_STROKE_WIDTH, shadowLayer, 0.35f, 0.90f),
                secondHand = AnalogHand(Color.parseColor("#8dc6cd"), SECOND_STROKE_WIDTH, shadowLayer, 0.95f, 1.0f),
                background = BitmapFactory.decodeResource(resources, R.drawable.bg_yume)
        )

        var currentIndex = 0
        val sets: List<HandSet> = listOf(
                shinobuSet, reinaSet, bluesnowSet, kumikoSet, yumeSet
        )

        private val mBackgroundPaint: Paint by lazy {
            val paint = Paint()
            paint.color = Color.BLACK
            paint
        }
        private var scaledBackgroundImage: Bitmap? = null
        private var mGrayBackgroundBitmap: Bitmap? = null
        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false


        override fun onCreate(holder: SurfaceHolder?) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@WasabiMinimal)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build())

            /* Extract colors from background image to improve watchface style. */
            /*
            Palette.from(scaledBackgroundImage!!).generate { palette ->
                if (palette != null) {
                    hourHand = hourHand.withColor(palette.getLightVibrantColor(Color.RED))
                    minuteHand = minuteHand.withColor(palette.getLightVibrantColor(Color.RED))
                    secondHand = secondHand.withColor(palette.getLightVibrantColor(Color.RED))
                }
            }*/

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

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                invalidate()
            }
        }

        fun reScaleBackground() {
            val currentBitmap = sets[currentIndex].background

            val width = mCenterX * 2f

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / currentBitmap!!.width.toFloat()

            scaledBackgroundImage = Bitmap.createScaledBitmap(currentBitmap,
                    (currentBitmap.width * scale).toInt(),
                    (currentBitmap.height * scale).toInt(), true
            )
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

            reScaleBackground()
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
                WatchFaceService.TAP_TYPE_TAP -> {
                    //Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT).show()
                    currentIndex = (currentIndex + 1) % sets.size
                    reScaleBackground()
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            val now = System.currentTimeMillis()
            mCalendar!!.timeInMillis = now

            val hourHand: ClockHand = sets[currentIndex].hourHand
            val minuteHand: ClockHand = sets[currentIndex].minuteHand
            val secondHand: ClockHand = sets[currentIndex].secondHand

            fun drawBackground(canvas: Canvas) {
                if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                    canvas.drawColor(Color.BLACK)
                } else {
                    canvas.drawBitmap(scaledBackgroundImage!!, 0f, 0f, mBackgroundPaint)
                }
            }
            drawBackground(canvas!!)
            //canvas!!.drawColor(Color.BLACK)

            360 / (60 * 60)

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds = mCalendar!!.get(Calendar.SECOND) + mCalendar!!.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f // 360 / 60

            val minutesRotation = mCalendar!!.get(Calendar.MINUTE) * 6f + (seconds / 10f)

            val hourHandOffset = mCalendar!!.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar!!.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(minutesRotation, mCenterX, mCenterY)
            minuteHand.drawOntoCanvas(canvas, mCenterX)

            canvas.rotate(hoursRotation - minutesRotation, mCenterX, mCenterY)
            hourHand.drawOntoCanvas(canvas, mCenterX)


            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                secondHand.drawOntoCanvas(canvas, mCenterX)

            }

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
        private val INTERACTIVE_UPDATE_RATE_MS = 33 //TimeUnit.SECONDS.toMillis(1)

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private val MSG_UPDATE_TIME = 0
    }
}