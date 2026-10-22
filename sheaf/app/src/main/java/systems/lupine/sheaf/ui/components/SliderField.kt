package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.data.model.CustomFieldOptions
import systems.lupine.sheaf.data.model.SliderLabels
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The slider custom field type: the control for editing a value and the
 * read-only bar for showing one.
 *
 * Two modes, as the server defines them (sheaf/services/slider_fields.py):
 * `labeled` is 0..100 between a left and a right label and reads as a
 * percentage; `numeric` is a quantity between min and max with an optional
 * unit. Either can paint a gradient across the track.
 *
 * The reading this produces is the same string the server puts in `value` on
 * a public profile, so a slider reads identically wherever it is shown.
 */

const val SLIDER_MODE_LABELED = "labeled"
const val SLIDER_MODE_NUMERIC = "numeric"

/** A slider's shape, every key resolved. */
data class SliderSpec(
    val mode: String,
    val min: Double,
    val max: Double,
    val step: Double?,
    val unit: String?,
    val labels: SliderLabels,
    val gradient: List<String>?,
) {
    val isNumeric: Boolean get() = mode == SLIDER_MODE_NUMERIC
    val span: Double get() = max - min
    val midpoint: Double get() = min + span / 2
    val hasAnyLabel: Boolean
        get() = !labels.left.isNullOrBlank() ||
            !labels.center.isNullOrBlank() ||
            !labels.right.isNullOrBlank()
}

/**
 * Resolve a field's options into a complete spec.
 *
 * The server fills every key in, but a slider is also the one field type
 * whose control cannot be drawn at all from a partial answer, so this reads
 * defensively: a hand-written options object, or a row from an instance older
 * than some key, still yields a track rather than a crash or a zero-width
 * bar. The defaults are the server's own.
 */
fun sliderSpec(options: CustomFieldOptions?): SliderSpec {
    val numeric = options?.mode == SLIDER_MODE_NUMERIC
    val min = if (numeric) options?.min ?: 0.0 else 0.0
    val max = if (numeric) options?.max ?: 10.0 else 100.0
    return SliderSpec(
        mode = if (numeric) SLIDER_MODE_NUMERIC else SLIDER_MODE_LABELED,
        // A min at or above max would give the control an empty or inverted
        // range to divide by, so fall back to the mode's own bounds.
        min = if (max > min) min else 0.0,
        max = if (max > min) max else if (numeric) 10.0 else 100.0,
        step = options?.step?.takeIf { it > 0 },
        unit = if (numeric) options?.unit?.takeIf { it.isNotBlank() } else null,
        labels = options?.labels ?: SliderLabels(),
        gradient = options?.gradient?.takeIf { it.size >= 2 },
    )
}

/** A new slider before anybody has configured it: the server's own defaults. */
fun defaultSliderSpec(): SliderSpec = SliderSpec(
    mode = SLIDER_MODE_LABELED,
    min = 0.0,
    max = 100.0,
    step = null,
    unit = null,
    labels = SliderLabels(),
    gradient = null,
)

/**
 * The options to send for this slider.
 *
 * Only the keys that mean something for the mode: a labeled slider's bounds
 * are the definition of the mode, so sending min/max there would be stating
 * something the server is going to ignore. Everything left out takes the
 * server's default, which is how a label, a unit or a gradient is cleared,
 * because a slider's options are replaced wholesale rather than merged.
 */
fun SliderSpec.toOptions(): CustomFieldOptions {
    val trimmed = SliderLabels(
        left = labels.left?.trim()?.ifBlank { null },
        center = labels.center?.trim()?.ifBlank { null },
        right = labels.right?.trim()?.ifBlank { null },
    )
    val stops = gradient?.takeIf { it.size >= 2 }
    return if (isNumeric) {
        CustomFieldOptions(
            mode = SLIDER_MODE_NUMERIC,
            min = min,
            max = max,
            step = step,
            unit = unit?.trim()?.ifBlank { null },
            labels = trimmed,
            gradient = stops,
        )
    } else {
        CustomFieldOptions(
            mode = SLIDER_MODE_LABELED,
            labels = trimmed,
            gradient = stops,
        )
    }
}

/** Trailing zeros are noise on a reading: 3.0 shows as 3, 2.5 stays 2.5. */
private fun tidyNumber(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

/** "62%" or "172.5 cm", the same reading the public profile serves. */
fun formatSliderValue(value: Any?, spec: SliderSpec): String {
    val number = (value as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: return ""
    val shown = tidyNumber(number)
    if (!spec.isNumeric) return "$shown%"
    return spec.unit?.let { "$shown $it" } ?: shown
}

/** Where a value sits on the track, 0..1. */
fun sliderFraction(value: Double, spec: SliderSpec): Float {
    if (spec.span <= 0) return 0f
    return ((value - spec.min) / spec.span).coerceIn(0.0, 1.0).toFloat()
}

/**
 * A slider's value as the control needs it: a number, or null when the field
 * is unset or holds something that is not a number (a value carried over from
 * when the field was text, say). Null reads as "not set" rather than as a
 * confident zero, which would be a value the member never chose.
 */
fun sliderValueOrNull(value: Any?): Double? =
    (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

/**
 * The value to actually store for a position on the track.
 *
 * Material's slider works in `Float`, and dragging to the 85 stop on a 0..100
 * track yields 85.00000762939453 once widened to a `Double`. The server takes
 * it, the reading still says "85%", and the number sitting in the database and
 * in every export is noise, where the web app stores a clean 85. So: snap to
 * the step grid when the field has one (which a labeled slider always does),
 * and otherwise round to the two decimals the reading shows, so the stored
 * number is the number the user was shown.
 */
fun snapSliderValue(value: Double, spec: SliderSpec): Double {
    val step = spec.step
    val snapped = if (step != null && step > 0) {
        spec.min + ((value - spec.min) / step).roundToInt() * step
    } else {
        (value * 100).roundToInt() / 100.0
    }
    return snapped.coerceIn(spec.min, spec.max)
}

/** The gradient as a brush, or null when the field has no gradient. */
private fun SliderSpec.trackBrush(): Brush? {
    val colours = gradient?.mapNotNull { parseColor(it) }?.takeIf { it.size >= 2 } ?: return null
    return Brush.horizontalGradient(colours)
}

private val TRACK_HEIGHT = 8.dp
private val READING_WIDTH = 72.dp

/**
 * The ends of the track in words. A numeric slider with no labels of its own
 * shows its bounds instead, so the numbers a value sits between are always
 * visible somewhere; a labeled one with no labels shows nothing, because
 * "0" and "100" say less than the reading beside the track already does.
 */
@Composable
private fun EndLabels(spec: SliderSpec) {
    val (left, right) = if (spec.isNumeric && !spec.hasAnyLabel) {
        val unit = spec.unit?.let { " $it" } ?: ""
        tidyNumber(spec.min) + unit to tidyNumber(spec.max) + unit
    } else {
        if (!spec.hasAnyLabel) return
        spec.labels.left.orEmpty() to spec.labels.right.orEmpty()
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        EndLabel(left, Modifier.weight(1f), TextAlign.Start)
        spec.labels.center?.takeIf { it.isNotBlank() }?.let {
            EndLabel(it, Modifier.weight(1f), TextAlign.Center)
        }
        EndLabel(right, Modifier.weight(1f), TextAlign.End)
    }
}

@Composable
private fun EndLabel(text: String, modifier: Modifier, align: TextAlign) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Editor. [value] is the current number or null for unset; [onChange] gets the
 * next number, or null when the value is cleared.
 *
 * An unset slider parks its handle at the midpoint and dims the control, the
 * same way the web one does: the handle has to be somewhere, and the middle is
 * the one position that does not read as an answer. The reading beside it says
 * "not set" until the user moves it, so parking is never mistaken for a value.
 */
@Composable
fun SliderInput(
    label: String,
    value: Double?,
    spec: SliderSpec,
    onChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = value ?: spec.midpoint
    val brush = spec.trackBrush()
    // Material's Slider counts the gaps between stops, not the stops. A step
    // that does not divide the range leaves the last stop short of max, so an
    // unrepresentable step is better dropped than silently capping the range.
    val steps = spec.step
        ?.takeIf { it > 0 && spec.span > 0 }
        ?.let { step ->
            val gaps = spec.span / step
            if (abs(gaps - gaps.roundToInt()) < 1e-9) (gaps.roundToInt() - 1).coerceAtLeast(0) else 0
        }
        ?: 0
    val reading = if (value != null) formatSliderValue(value, spec) else "not set"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = current.toFloat(),
                onValueChange = { onChange(snapSliderValue(it.toDouble(), spec)) },
                valueRange = spec.min.toFloat()..spec.max.toFloat(),
                steps = steps,
                // The track is drawn here rather than left to Material for one
                // reason: a gradient cannot be expressed as a track colour, so
                // a gradient slider would otherwise be a different SHAPE from
                // one without, and the two sit next to each other in the same
                // member's fields. Same bar either way, and the same bar the
                // read-only readout and the settings preview draw.
                track = {
                    SliderTrack(
                        fraction = sliderFraction(current, spec),
                        brush = brush,
                        dimmed = value == null,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$label, $reading" },
            )
            Text(
                reading,
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(READING_WIDTH),
            )
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        EndLabels(spec)
    }
}

/**
 * The bar itself: the gradient when the field has one, otherwise the usual
 * filled-to-here track. Dimmed while the value is unset, so a parked handle
 * does not read as an answer.
 */
@Composable
private fun SliderTrack(fraction: Float, brush: Brush?, dimmed: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .alpha(if (dimmed) 0.6f else 1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (brush != null) {
            Box(Modifier.matchParentSize().background(brush))
        } else {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Read-only: the bar with a marker at the value, and the reading beside it. */
@Composable
fun SliderReadout(
    value: Any?,
    spec: SliderSpec,
    modifier: Modifier = Modifier,
) {
    val number = sliderValueOrNull(value) ?: return
    val fraction = sliderFraction(number, spec)
    val brush = spec.trackBrush()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val markerSize = 14.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TRACK_HEIGHT)
                        .clip(CircleShape)
                        .then(
                            if (brush != null) {
                                Modifier.background(brush)
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            },
                        ),
                )
                Box(
                    modifier = Modifier
                        .offset(
                            // Inset by half the marker so it stays on the bar
                            // at both ends rather than hanging off them.
                            x = (maxWidth - markerSize) * fraction,
                            y = (TRACK_HEIGHT - markerSize) / 2,
                        )
                        .size(markerSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatSliderValue(number, spec),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(READING_WIDTH),
            )
        }
        EndLabels(spec)
    }
}
