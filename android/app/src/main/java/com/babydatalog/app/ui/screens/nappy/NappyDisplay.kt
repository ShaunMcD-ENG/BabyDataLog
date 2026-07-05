package com.babydatalog.app.ui.screens.nappy

import com.babydatalog.app.data.database.entity.NappyAmount
import com.babydatalog.app.data.database.entity.NappyChange
import com.babydatalog.app.data.database.entity.PooColour

fun NappyAmount.displayLabel(): String = when (this) {
    NappyAmount.NONE -> "None"
    NappyAmount.SMALL -> "Small"
    NappyAmount.MEDIUM -> "Medium"
    NappyAmount.LARGE -> "Large"
}

fun PooColour.displayLabel(): String = when (this) {
    PooColour.NA -> "N/A"
    PooColour.MECONIUM -> "Newborn Black (Meconium)"
    PooColour.DARK_GREEN -> "Dark Green / Brown"
    PooColour.YELLOW_SEEDY -> "Yellow Seedy (normal breastfed)"
    PooColour.BRIGHT_YELLOW -> "Bright Yellow"
    PooColour.GREEN -> "Green"
    PooColour.BROWN -> "Brown"
    PooColour.PALE_WHITE -> "Pale / White ⚠ See doctor"
    PooColour.RED_BLOOD -> "Red / Blood ⚠ See doctor"
}

fun NappyChange.contentsSummary(): String {
    val parts = mutableListOf<String>()
    if (weeAmount != NappyAmount.NONE) parts += "Wee (${weeAmount.displayLabel()})"
    if (pooAmount != NappyAmount.NONE) parts += "Poo (${pooAmount.displayLabel()})"
    return if (parts.isEmpty()) "No wee/poo recorded" else parts.joinToString(" + ")
}
