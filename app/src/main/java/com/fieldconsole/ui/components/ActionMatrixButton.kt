package com.fieldconsole.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ActionMatrixButton — a large, coloured, labelled button for the Action Matrix.
 */
@Composable
fun ActionMatrixButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Determine readable text/icon colour based on background luminance
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    val contentColor = if (luminance > 0.55f) Color.Black else Color.White

    Button(
        onClick  = onClick,
        enabled  = enabled,
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor         = color,
            contentColor           = contentColor,
            disabledContainerColor = color.copy(alpha = 0.38f),
            disabledContentColor   = contentColor.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                modifier           = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 16.sp,
                maxLines   = 2
            )
        }
    }
}
