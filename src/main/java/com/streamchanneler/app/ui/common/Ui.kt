// TODO: Validate
package com.streamchanneler.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.streamchanneler.app.data.DEFAULT_ZOOM
import com.streamchanneler.app.streamChannelerApp

// TODO: Validate
object UiColors {
    // TODO: Validate
    val BACKGROUND = Color.parseColor("#0A0A0A")
    // TODO: Validate
    val SURFACE = Color.parseColor("#171717")
    // TODO: Validate
    val SURFACE_RAISED = Color.parseColor("#222222")
    // TODO: Validate
    val HEADER = Color.parseColor("#262626")
    // TODO: Validate
    val BORDER = Color.parseColor("#303030")
    // TODO: Validate
    val TEXT = Color.parseColor("#FAFAFA")
    // TODO: Validate
    val TEXT_SECONDARY = Color.parseColor("#D4D4D4")
    // TODO: Validate
    val TEXT_MUTED = Color.parseColor("#A3A3A3")
    // TODO: Validate
    val ACCENT = Color.parseColor("#14B8A6")
    // TODO: Validate
    val ON_ACCENT = Color.parseColor("#042F2E")
    // TODO: Validate
    val ACCENT_TINT = Color.parseColor("#1A14B8A6")
    // TODO: Validate
    val NOW_LINE = Color.parseColor("#EF4444")
    // TODO: Validate
    val STAR = Color.parseColor("#FACC15")
    // TODO: Validate
    val ERROR = Color.parseColor("#F87171")
}

// TODO: Validate
private const val CONTROL_CORNER_RADIUS = 6f

// TODO: Validate
fun Context.zoomedContext(): Context {
    val zoom = streamChannelerApp.localAppStorage.zoom
    if (zoom == DEFAULT_ZOOM) return this
    val zoomedConfiguration = Configuration(resources.configuration).apply { densityDpi = densityDpi * zoom / 100 }
    return createConfigurationContext(zoomedConfiguration)
}

// TODO: Validate
fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

// TODO: Validate
fun Context.dpFloat(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

// TODO: Validate
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

// TODO: Validate
fun roundedBackground(fillColor: Int, strokeColor: Int, cornerRadius: Float, strokeWidth: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(fillColor)
        this.cornerRadius = cornerRadius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

// TODO: Validate
private fun Context.focusableBackground(): Drawable {
    val cornerRadius = dpFloat(CONTROL_CORNER_RADIUS)
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), roundedBackground(UiColors.ACCENT, UiColors.TEXT, cornerRadius, dp(2)))
        for (accentFilledState in intArrayOf(android.R.attr.state_pressed, android.R.attr.state_activated)) {
            addState(intArrayOf(accentFilledState), roundedBackground(UiColors.ACCENT, UiColors.ACCENT, cornerRadius, dp(1)))
        }
        addState(intArrayOf(), roundedBackground(UiColors.SURFACE, UiColors.BORDER, cornerRadius, dp(1)))
    }
}

// TODO: Validate
private fun focusableTextColors(): ColorStateList = ColorStateList(
    arrayOf(
        intArrayOf(android.R.attr.state_focused),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf(android.R.attr.state_activated),
        intArrayOf(),
    ),
    intArrayOf(UiColors.ON_ACCENT, UiColors.ON_ACCENT, UiColors.ON_ACCENT, UiColors.TEXT),
)

// TODO: Validate
fun Context.actionButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 15f
    stateListAnimator = null
    background = focusableBackground()
    setTextColor(focusableTextColors())
    minHeight = dp(40)
    minimumHeight = dp(40)
    minWidth = 0
    minimumWidth = 0
    setPadding(dp(14), dp(6), dp(14), dp(6))
    isFocusable = true
    setOnClickListener { onClick() }
}

// TODO: Validate
fun Context.textInput(hint: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
    val cornerRadius = dpFloat(CONTROL_CORNER_RADIUS)
    this.hint = hint
    this.inputType = inputType
    setSingleLine(true)
    imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    textSize = 16f
    setTextColor(UiColors.TEXT)
    setHintTextColor(UiColors.TEXT_MUTED)
    background = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_focused),
            roundedBackground(UiColors.SURFACE, UiColors.ACCENT, cornerRadius, dp(2)),
        )
        addState(intArrayOf(), roundedBackground(UiColors.SURFACE, UiColors.BORDER, cornerRadius, dp(1)))
    }
    setPadding(dp(12), dp(8), dp(12), dp(8))
}

// TODO: Validate
fun Context.unfocusableTextLabel(text: String = "", textSize: Float = 14f, color: Int = UiColors.TEXT, isBold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        this.textSize = textSize
        setTextColor(color)
        if (isBold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

// TODO: Validate
fun Context.centeredTextLabel(text: String, textSize: Float, color: Int, isBold: Boolean = false): TextView =
    unfocusableTextLabel(text, textSize, color, isBold).apply { gravity = Gravity.CENTER }

// TODO: Validate
fun Context.fullWidthLayoutParams(topMargin: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .also { layoutParams -> layoutParams.topMargin = dp(topMargin) }

// TODO: Validate
fun Context.overlayMessageView(messageText: String): TextView = TextView(this).apply {
    text = messageText
    setTextColor(UiColors.TEXT)
    textSize = 16f
    setPadding(dp(16), dp(10), dp(16), dp(10))
    background = roundedBackground(UiColors.SURFACE, UiColors.ACCENT, dp(8).toFloat(), dp(1))
}
