package com.forge.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.foundry.FoundryScreen
import com.forge.workshop.foundry.SkillSpec
import com.forge.workshop.nav.NavItem
import com.forge.workshop.nav.NavRail
import com.forge.workshop.theme.forgeColors

/** Root of the main Workshop window: nav rail + the selected screen. */
@Composable
fun WorkshopApp() {
    var selected by remember { mutableStateOf(NavItem.FOUNDRY) }

    Surface(color = forgeColors.ground, modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavRail(selected) { selected = it }
            Box(Modifier.width(1.dp).fillMaxHeight().background(forgeColors.border))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (selected) {
                    NavItem.FOUNDRY -> FoundryScreen(onRun = ::onRunSkill)
                    NavItem.HISTORY -> Placeholder("History — скоро")
                    NavItem.INTEGRATIONS -> Placeholder("Integrations — скоро")
                }
            }
        }
    }
}

/** Step 3 wires this to the Skill Runner + MasterGate; for now it is a no-op landing point. */
private fun onRunSkill(skill: SkillSpec) {
    // TODO(P4 step 3): open Skill Runner for `skill`
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = forgeColors.inkFaint, fontSize = 15.sp)
    }
}
