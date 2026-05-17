package systems.lupine.sheaf.wear.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition

/**
 * Standard wear OS scaffolding for any screen whose body is a
 * [ScalingLazyColumn]: TimeText at the top, a PositionIndicator
 * scrollbar bound to the list state (required by Play review for
 * lists), and a top+bottom vignette.
 *
 * Screens with non-list bodies (login form, etc.) keep using
 * [Scaffold] directly; this helper is just to eliminate the boilerplate
 * that every list screen previously copy-pasted minus the scrollbar.
 */
@Composable
fun SheafScalingLazyScaffold(
    listState: ScalingLazyListState = rememberScalingLazyListState(),
    modifier: Modifier = Modifier.fillMaxSize(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = contentPadding,
            content = content,
        )
    }
}
