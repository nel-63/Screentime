package com.example.screentimeguard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Taille de la maquette Figma (iPhone 17 : 402 x 874). Tout est mis à l'échelle à partir de là.
private const val DESIGN_WIDTH = 402f
private const val DESIGN_HEIGHT = 874f

// Couleurs lues dans le Figma
private val LockerBackground = Color(0xFF0B090A)
private val LockerTitle = Color(0xFFF5F3F4)
private val LockerRed = Color(0xFFBA181B)
private val LockerGreyBottom = Color(0xFFD3D3D3)
// Le gris du haut vaut #B1A7A6 dans le Figma ; l'opacité 0,7 est déduite de la capture (rendu ≈ #7F7877).
private val LockerGreyTop = Color(0xFFB1A7A6).copy(alpha = 0.7f)

// ⚠️ La police du design est « Lilita One ». Tant que son fichier n'est pas dans res/font,
// on utilise la police du système en gras. Pour activer la vraie police (étape 2), remplace
// les deux lignes ci-dessous par :
//   private val LockerFont = FontFamily(Font(R.font.lilita_one_regular))
//   private val LockerFontWeight = FontWeight.Normal
// et ajoute : import androidx.compose.ui.text.font.Font
private val LockerFont: FontFamily = FontFamily.Default
private val LockerFontWeight = FontWeight.ExtraBold

@Composable
fun LockerHomeScreen(
    onSettings: () -> Unit,
    onStats: () -> Unit,
    onLimits: () -> Unit,
    onApps: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LockerBackground)
    ) {
        val screenHeight = maxHeight
        val widthScale = maxWidth.value / DESIGN_WIDTH

        // Les deux formes diagonales du fond, prolongées jusqu'aux bords de l'écran.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sx = size.width / DESIGN_WIDTH
            val sy = size.height / DESIGN_HEIGHT

            val topRight = Path().apply {
                moveTo(7f * sx, 1f * sy)
                lineTo(DESIGN_WIDTH * sx, 1f * sy)
                lineTo(DESIGN_WIDTH * sx, 462f * sy)
                close()
            }
            drawPath(topRight, LockerGreyTop)

            val bottomLeft = Path().apply {
                moveTo(0f, 452f * sy)
                lineTo(DESIGN_WIDTH * sx, DESIGN_HEIGHT * sy)
                lineTo(0f, DESIGN_HEIGHT * sy)
                close()
            }
            drawPath(bottomLeft, LockerGreyBottom)
        }

        Text(
            text = "LOCKER",
            color = LockerTitle,
            fontFamily = LockerFont,
            fontWeight = LockerFontWeight,
            fontSize = (96f * widthScale).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = screenHeight * (70f / DESIGN_HEIGHT))
        )

        // Chaque bouton sort de l'écran par la gauche : seuls les coins de droite sont arrondis.
        // widthFraction = bord droit du bouton dans le Figma / 402.
        LockerMenuButton(
            label = "PARAMÈTRES",
            icon = Icons.Filled.Settings,
            topOffset = screenHeight * (340f / DESIGN_HEIGHT),
            widthFraction = 215f / DESIGN_WIDTH,
            iconEndPadding = 10.dp,
            onClick = onSettings
        )
        LockerMenuButton(
            label = "STATISTIQUE",
            icon = Icons.Filled.DateRange,
            topOffset = screenHeight * (452f / DESIGN_HEIGHT),
            widthFraction = 235f / DESIGN_WIDTH,
            iconEndPadding = 18.dp,
            onClick = onStats
        )
        LockerMenuButton(
            label = "DÉFINIR LIMITES",
            icon = Icons.Filled.Refresh,
            topOffset = screenHeight * (562f / DESIGN_HEIGHT),
            widthFraction = 268f / DESIGN_WIDTH,
            iconEndPadding = 14.dp,
            onClick = onLimits
        )
        LockerMenuButton(
            label = "APPLICATION SURVEILLÉE",
            icon = Icons.Filled.Menu,
            topOffset = screenHeight * (672f / DESIGN_HEIGHT),
            widthFraction = 374f / DESIGN_WIDTH,
            iconEndPadding = 7.dp,
            onClick = onApps
        )
    }
}

@Composable
private fun LockerMenuButton(
    label: String,
    icon: ImageVector,
    topOffset: Dp,
    widthFraction: Float,
    iconEndPadding: Dp,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .offset(y = topOffset)
            .fillMaxWidth(widthFraction)
            .height(61.dp)
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .background(LockerRed)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 21.dp, end = iconEndPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = LockerFont,
            fontWeight = LockerFontWeight,
            fontSize = 24.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.weight(1f)
        )
        // Icône provisoire (Material) : voir étape 3 pour utiliser celles du Figma.
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 402, heightDp = 874)
@Composable
private fun LockerHomeScreenPreview() {
    LockerHomeScreen(onSettings = {}, onStats = {}, onLimits = {}, onApps = {})
}