package com.babydatalog.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BabyDataLogShapes = Shapes(
    // Extra small — chips, small text fields
    extraSmall = RoundedCornerShape(4.dp),
    // Small — small buttons, snackbars
    small = RoundedCornerShape(8.dp),
    // Medium — cards, dialogs, menus
    medium = RoundedCornerShape(16.dp),
    // Large — bottom sheets, navigation drawers
    large = RoundedCornerShape(24.dp),
    // Extra large — full-screen dialogs
    extraLarge = RoundedCornerShape(28.dp)
)
