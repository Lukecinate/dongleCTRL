package com.suprcharg3d.dongleCTRL.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared top header for the three primary bottom-bar tabs (DEVICES, PRESETS, SETTINGS)
 * so title typography, weight, and vertical baseline remain identical across tabs.
 */
@Composable
fun MainTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingBadge: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 14.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
        if (leadingBadge != null) {
            leadingBadge()
        }
        Spacer(modifier = Modifier.weight(1f))
        if (actions != null) {
            actions()
        }
    }
}
