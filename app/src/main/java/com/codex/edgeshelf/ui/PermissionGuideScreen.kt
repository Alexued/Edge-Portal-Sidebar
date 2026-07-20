package com.codex.edgeshelf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.edgeshelf.R
import com.codex.edgeshelf.ui.theme.Jade
import com.codex.edgeshelf.ui.theme.WarningSoft

@Composable
internal fun PermissionGuide(
    permissions: List<PermissionItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        permissions.forEachIndexed { index, permission ->
            PermissionRow(permission)
            if (index != permissions.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: PermissionItem) {
    val status = stringResource(if (permission.granted) R.string.granted else R.string.not_granted)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.open_settings),
                onClick = permission.onClick,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (permission.granted) MaterialTheme.colorScheme.primaryContainer else WarningSoft,
                    CircleShape,
                )
                .semantics { contentDescription = status },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (permission.granted) "\u2713" else permission.index.toString(),
                color = if (permission.granted) Jade else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 13.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    permission.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    permission.requirement,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                permission.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = if (permission.granted) status else stringResource(R.string.open_settings),
            color = if (permission.granted) Jade else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal data class PermissionItem(
    val index: Int,
    val title: String,
    val description: String,
    val requirement: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)
