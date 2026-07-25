package com.forge.workshop.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.workshop.data.AppDataStore
import com.forge.workshop.theme.forgeColors

/** Lists saved recipes with New / Edit / Delete; the board opens from here. */
@Composable
fun RecipeListScreen(
    store: AppDataStore,
    onNew: () -> Unit,
    onEdit: (SavedRecipe) -> Unit,
    onRun: (SavedRecipe) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Recipes", color = forgeColors.ink, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Собери свой процесс из страйков на доске", color = forgeColors.inkFaint, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(forgeColors.ember).clickable { onNew() }.padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text("+ Новый рецепт", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(18.dp))

        val recipes = store.data.recipes
        if (recipes.isEmpty()) {
            Text("Пока пусто — нажмите «Новый рецепт» и соберите граф на доске.", color = forgeColors.inkMuted, fontSize = 14.sp)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recipes.forEach { r -> RecipeRow(r, onRun = { onRun(r) }, onEdit = { onEdit(r) }, onDelete = { store.deleteRecipe(r.id) }) }
            }
        }
    }
}

@Composable
private fun RecipeRow(recipe: SavedRecipe, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val strikeCount = recipe.nodes.count { it.typeId != START_ID && it.typeId != END_ID }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(forgeColors.surface2)
            .border(1.dp, forgeColors.border, RoundedCornerShape(10.dp)).clickable { onEdit() }.padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(recipe.name, color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("$strikeCount страйк(ов) · ${recipe.links.size} нитей", color = forgeColors.inkMuted, fontSize = 12.sp)
        }
        Text("▶ запустить", color = forgeColors.good, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRun() }.padding(8.dp))
        Spacer(Modifier.width(6.dp))
        Text("править", color = forgeColors.ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onEdit() }.padding(8.dp))
        Spacer(Modifier.width(6.dp))
        Text("удалить", color = forgeColors.crit, fontSize = 12.sp, modifier = Modifier.clickable { onDelete() }.padding(8.dp))
    }
}
