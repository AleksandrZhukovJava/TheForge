package com.forge.workshop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.forge.workshop.foundry.ExecTag
import com.forge.workshop.theme.forgeColors

/** Shared executor → color mapping so cards, chips and the runner timeline agree. */
@Composable
fun execColor(tag: ExecTag): Color = when (tag) {
    ExecTag.TOOL -> forgeColors.tool
    ExecTag.PRESS -> forgeColors.press
    ExecTag.MASTER -> forgeColors.master
    ExecTag.SMITH -> forgeColors.smith
}
