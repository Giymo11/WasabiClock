package science.wasabi.wear.minimal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast
import science.wasabi.wear.components.AnalogHand
import science.wasabi.wear.components.ClockDrawable
import science.wasabi.wear.components.ShadowLayer
import science.wasabi.wear.components.Ticks
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 */
class WasabiElegant : CanvasWatchFaceService() {

    override fun onCreateEngine(): CanvasWatchFaceService.Engine {
        return Engine()
    }

    private class EngineHandler(reference: WasabiElegant.Engine) : Handler() {
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

        private val HOUR_STROKE_WIDTH = 4f
        private val MINUTE_STROKE_WIDTH = 2.5f
        private val TICK_STROKE_WIDTH = 1f

        val shadowLayer = ShadowLayer(SHADOW_RADIUS.toFloat(), 0f, 0f, defaultWatchHandShadowColor)
        private var hourHand: ClockDrawable = AnalogHand(defaultWatchHandColor, HOUR_STROKE_WIDTH, shadowLayer, 0.0f, 0.33f)
        private var minuteHand: ClockDrawable = AnalogHand(defaultWatchHandColor, MINUTE_STROKE_WIDTH, shadowLayer, 0.33f, 0.9f)
        private val ticks = Ticks(Color.parseColor("#888888"), TICK_STROKE_WIDTH, shadowLayer, 0.82f, 1.0f)

        private var mBackgroundPaint: Paint? = null
        private var mBackgroundBitmap: Bitmap? = null
        private var mGrayBackgroundBitmap: Bitmap? = null
        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false


        override fun onCreate(holder: SurfaceHolder?) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@WasabiElegant)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build())

            mBackgroundPaint = Paint()
            mBackgroundPaint!!.color = Color.BLACK
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.black)

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
            } else {
                canvas!!.drawBitmap(mBackgroundBitmap!!, 0f, 0f, mBackgroundPaint)
            }

            ticks.drawOntoCanvas(canvas, mCenterX, mAmbient)

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val minutesRotation = mCalendar!!.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar!!.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar!!.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            hourHand.drawOntoCanvas(canvas, mCenterX, mAmbient)

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            minuteHand.drawOntoCanvas(canvas, mCenterX, mAmbient)

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
            this@WasabiElegant.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WasabiElegant.unregisterReceiver(mTimeZoneReceiver)
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