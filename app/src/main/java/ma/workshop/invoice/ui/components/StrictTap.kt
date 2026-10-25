package ma.workshop.invoice.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The app's one tap rule. Compose's plain clickable fires when the finger lifts even if it has slid far away,
 * so sweeping a finger across the screen pressed whatever it crossed. This version counts a tap only when the
 * finger stays within the system touch slop, uses a single pointer, and lifts. Anything else is cancelled.
 */
fun Modifier.strictClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val ownSource = remember { MutableInteractionSource() }
    val interactionSource = interactionSource ?: ownSource
    val currentOnClick by rememberUpdatedState(onClick)
    val slop = LocalViewConfiguration.current.touchSlop
    this
        .indication(interactionSource, LocalIndication.current)
        .semantics(mergeDescendants = true) {
            role?.let { this.role = it }
            if (enabled) {
                this.onClick(label = onClickLabel, action = {
                    currentOnClick()
                    true
                })
            } else {
                disabled()
            }
        }
        .pointerInput(enabled, slop) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                var cancelled = false
                var last: PointerInputChange = down
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    // A second finger, or the first finger leaving the pointer stream, is never a tap.
                    if (change == null || event.changes.count { it.pressed } > 1) cancelled = true
                    if (change != null) {
                        last = change
                        // Moved too far (a scroll or a slide), or a control nested inside already took this touch.
                        if ((change.position - down.position).getDistance() > slop || change.isConsumed) cancelled = true
                    }
                    released = change == null || !change.pressed
                }
                if (cancelled) {
                    interactionSource.tryEmit(PressInteraction.Cancel(press))
                } else {
                    interactionSource.tryEmit(PressInteraction.Release(press))
                    last.consume()
                    currentOnClick()
                }
            }
        }
}

/**
 * A button drawn as a surface, so every button follows [strictClickable]. Looks like the Material button it replaces:
 * pill-shaped by default, content centred, ripple clipped to the shape.
 */
@Composable
fun StrictButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    shape: Shape = CircleShape,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    /** For buttons without words (a speaker tile): the name screen readers and tests find it by. */
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .strictClickable(enabled = enabled, onClickLabel = onClickLabel, interactionSource = interactionSource, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Icon-only button under the same tap rule. The caller sets the size, as with Material's IconButton. */
@Composable
fun StrictIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            // The name screen readers (and tests) find the button by; onClickLabel alone only names the action.
            .semantics { this.contentDescription = contentDescription }
            .strictClickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
