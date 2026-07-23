package com.forge.workshop.runner

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.sdk.master.ConfirmRequest
import com.forge.workshop.theme.forgeColors

/** The Master confirmation modal — shown for CONFIRM-level Strikes (writes to external systems). */
@Composable
fun ConfirmModal(request: ConfirmRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(410.dp).clip(RoundedCornerShape(14.dp)).background(forgeColors.surface1)
                .border(1.dp, forgeColors.borderStrong, RoundedCornerShape(14.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(forgeColors.master))
                Spacer(Modifier.width(10.dp))
                Text("Требуется подтверждение", color = forgeColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, forgeColors.master.copy(alpha = 0.5f), RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text("Master", color = forgeColors.master, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(forgeColors.border))
            Column(Modifier.padding(18.dp)) {
                Text("Шаг изменяет внешнюю систему — нужно ваше подтверждение.", color = forgeColors.inkMuted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                KvRow("summary", request.summary)
                KvRow("capability", request.capability.value)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(forgeColors.border))
            Row(
                modifier = Modifier.fillMaxWidth().background(forgeColors.surface2).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                GhostButton("Отклонить", onReject)
                PrimaryButton("Подтвердить", onApprove)
            }
        }
    }
}

@Composable
private fun KvRow(key: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(key, color = forgeColors.inkFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(92.dp))
        Text(value, color = forgeColors.ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, forgeColors.borderStrong, RoundedCornerShape(9.dp)).clickable { onClick() }.padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(text, color = forgeColors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(forgeColors.ember).clickable { onClick() }.padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
