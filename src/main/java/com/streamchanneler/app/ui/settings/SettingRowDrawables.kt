// TODO: Validate
package com.streamchanneler.app.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import com.streamchanneler.app.ui.common.UiColors
import com.streamchanneler.app.ui.common.dp
import com.streamchanneler.app.ui.common.dpFloat
import com.streamchanneler.app.ui.common.sp

// TODO: Validate
enum class SettingSwitchState { ON, OFF, MIXED }

// TODO: Validate
fun onOrOffSettingSwitchState(isOn: Boolean): SettingSwitchState = if (isOn) SettingSwitchState.ON else SettingSwitchState.OFF

// TODO: Validate
fun sharedOrMixedSettingSwitchState(isOnInEachSite: List<Boolean>): SettingSwitchState =
    if (isOnInEachSite.distinct().size > 1) SettingSwitchState.MIXED else onOrOffSettingSwitchState(true in isOnInEachSite)

// TODO: Validate
private fun isAccentFilledState(drawableState: IntArray): Boolean = drawableState.any { stateAttribute ->
    stateAttribute == android.R.attr.state_focused ||
        stateAttribute == android.R.attr.state_pressed ||
        stateAttribute == android.R.attr.state_activated
}

// TODO: Validate
abstract class SettingRowEndDrawable : Drawable() {
    // TODO: Validate
    protected var isOnAccentFill = false
        private set

    // TODO: Validate
    override fun isStateful(): Boolean = true

    // TODO: Validate
    override fun onStateChange(state: IntArray): Boolean {
        val isNowOnAccentFill = isAccentFilledState(state)
        if (isNowOnAccentFill == isOnAccentFill) return false
        isOnAccentFill = isNowOnAccentFill
        invalidateSelf()
        return true
    }

    // TODO: Validate
    override fun setAlpha(alpha: Int) = Unit

    // TODO: Validate
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    // TODO: Validate
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// TODO: Validate
class SettingSwitchDrawable(context: Context, private val switchState: SettingSwitchState) : SettingRowEndDrawable() {
    // TODO: Validate
    private val switchWidth = context.dp(44)
    // TODO: Validate
    private val switchHeight = context.dp(24)
    // TODO: Validate
    private val knobInset = context.dpFloat(3f)
    // TODO: Validate
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // TODO: Validate
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // TODO: Validate
    override fun getIntrinsicWidth(): Int = switchWidth

    // TODO: Validate
    override fun getIntrinsicHeight(): Int = switchHeight

    // TODO: Validate
    override fun draw(canvas: Canvas) {
        val trackBounds = RectF(bounds)
        val trackCornerRadius = trackBounds.height() / 2
        trackPaint.color = when {
            switchState == SettingSwitchState.ON && isOnAccentFill -> UiColors.ON_ACCENT
            switchState == SettingSwitchState.ON -> UiColors.ACCENT
            isOnAccentFill -> SWITCH_TRACK_ON_ACCENT_FILL
            else -> UiColors.BORDER
        }
        canvas.drawRoundRect(trackBounds, trackCornerRadius, trackCornerRadius, trackPaint)
        val knobDiameter = trackBounds.height() - knobInset * 2
        val knobTravel = trackBounds.width() - knobInset * 2 - knobDiameter
        val knobTravelFraction = when (switchState) {
            SettingSwitchState.ON -> 1f
            SettingSwitchState.OFF -> 0f
            SettingSwitchState.MIXED -> 0.5f
        }
        knobPaint.color = if (switchState == SettingSwitchState.ON) UiColors.TEXT else UiColors.TEXT_MUTED
        canvas.drawCircle(
            trackBounds.left + knobInset + knobTravel * knobTravelFraction + knobDiameter / 2,
            trackBounds.centerY(),
            knobDiameter / 2,
            knobPaint,
        )
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val SWITCH_TRACK_ON_ACCENT_FILL = 0x59042F2E
    }
}

// TODO: Validate
class SettingValueDrawable(context: Context, valueText: String) : SettingRowEndDrawable() {
    // TODO: Validate
    private val horizontalPadding = context.dpFloat(10f)
    // TODO: Validate
    private val verticalPadding = context.dpFloat(4f)
    // TODO: Validate
    private val boxCornerRadius = context.dpFloat(6f)
    // TODO: Validate
    private val boxStrokeWidth = context.dpFloat(1f)
    // TODO: Validate
    private val valueTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = context.sp(14f) }
    // TODO: Validate
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // TODO: Validate
    private val shownValueText = TextUtils.ellipsize(
        valueText,
        valueTextPaint,
        context.dpFloat(MAXIMUM_VALUE_TEXT_WIDTH),
        TextUtils.TruncateAt.END,
    ).toString()

    // TODO: Validate
    override fun getIntrinsicWidth(): Int = (valueTextPaint.measureText(shownValueText) + horizontalPadding * 2).toInt()

    // TODO: Validate
    override fun getIntrinsicHeight(): Int =
        (valueTextPaint.fontMetrics.let { it.descent - it.ascent } + verticalPadding * 2).toInt()

    // TODO: Validate
    override fun draw(canvas: Canvas) {
        val boxBounds = RectF(bounds).apply { inset(boxStrokeWidth / 2, boxStrokeWidth / 2) }
        if (!isOnAccentFill) {
            boxPaint.style = Paint.Style.FILL
            boxPaint.color = UiColors.BACKGROUND
            canvas.drawRoundRect(boxBounds, boxCornerRadius, boxCornerRadius, boxPaint)
        }
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = boxStrokeWidth
        boxPaint.color = if (isOnAccentFill) UiColors.ON_ACCENT else UiColors.BORDER
        canvas.drawRoundRect(boxBounds, boxCornerRadius, boxCornerRadius, boxPaint)
        valueTextPaint.color = if (isOnAccentFill) UiColors.ON_ACCENT else UiColors.TEXT_SECONDARY
        val valueTextBaseline = bounds.exactCenterY() - (valueTextPaint.fontMetrics.ascent + valueTextPaint.fontMetrics.descent) / 2
        canvas.drawText(shownValueText, bounds.left + horizontalPadding, valueTextBaseline, valueTextPaint)
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val MAXIMUM_VALUE_TEXT_WIDTH = 320f
    }
}
