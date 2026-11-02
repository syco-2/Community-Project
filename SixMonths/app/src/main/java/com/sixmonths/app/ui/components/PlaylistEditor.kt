package com.sixmonths.app.ui.components
import com.sixmonths.app.ui.components.AppTextButton

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.displayFileName

/** SAF playlist editor: preview, remove and reorder. No playback controls here. */
@Composable
fun PlaylistEditor(
    uris: List<String>,
    onAddMusic: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    SixMonthsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AppTextButton(onClick = onAddMusic) {
                Text(text = "+ Add music", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (uris.isEmpty()) {
            Text(
                text = "No music",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )
        } else {
            uris.forEachIndexed { index, rawUri ->
                val name = remember(rawUri) { Uri.parse(rawUri).displayFileName(context) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0,
                    ) { Text(text = "↑") }
                    AppTextButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < uris.lastIndex,
                    ) { Text(text = "↓") }
                    AppTextButton(onClick = { onRemove(rawUri) }) {
                        Text(text = "✕", color = MaterialTheme.sixMonthsColors.danger)
                    }
                }
            }
        }
    }
}
