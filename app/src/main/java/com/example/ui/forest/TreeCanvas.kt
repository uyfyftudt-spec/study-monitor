package com.example.ui.forest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.TreeGrowthStage

@Composable
fun TreeCanvas(
    stage: TreeGrowthStage,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Common Earth / Soil Mound at the bottom with soft gradient
        drawSoilMound(w, h)

        when (stage) {
            TreeGrowthStage.SEEDLING -> drawSeedling(w, h)
            TreeGrowthStage.SAPLING -> drawSapling(w, h)
            TreeGrowthStage.YOUNG_TREE -> drawYoungTree(w, h)
            TreeGrowthStage.BLOOMING_TREE -> drawBloomingTree(w, h)
            TreeGrowthStage.MATURE_TREE -> drawMatureTree(w, h)
        }
    }
}

private fun DrawScope.drawSoilMound(w: Float, h: Float) {
    val moundWidth = w * 0.78f
    val moundHeight = h * 0.16f
    val left = (w - moundWidth) / 2f
    val top = h * 0.83f

    // Rich organic soil with subtle linear gradient
    val soilBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF6D4C41), Color(0xFF3E2723)),
        startY = top,
        endY = top + moundHeight
    )
    drawOval(
        brush = soilBrush,
        topLeft = Offset(left, top),
        size = Size(moundWidth, moundHeight)
    )

    // Lush grass patch on top of soil with soft gradient
    val grassBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF4CAF50), Color(0xFF2E7D32)),
        startY = top,
        endY = top + moundHeight * 0.55f
    )
    drawOval(
        brush = grassBrush,
        topLeft = Offset(left + moundWidth * 0.08f, top),
        size = Size(moundWidth * 0.84f, moundHeight * 0.52f)
    )

    // Subtle grass blade accents
    val bladeColor = Color(0xFF81C784)
    drawLine(
        color = bladeColor,
        start = Offset(w * 0.42f, top + 4f),
        end = Offset(w * 0.40f, top - 6f),
        strokeWidth = 2.5f
    )
    drawLine(
        color = bladeColor,
        start = Offset(w * 0.58f, top + 4f),
        end = Offset(w * 0.60f, top - 6f),
        strokeWidth = 2.5f
    )
}

private fun DrawScope.drawSeedling(w: Float, h: Float) {
    val centerX = w * 0.5f
    val groundY = h * 0.84f
    val sproutTopY = h * 0.62f

    // Elegant curved sprout stem
    val stemPath = Path().apply {
        moveTo(centerX, groundY)
        cubicTo(
            centerX - w * 0.05f, h * 0.76f,
            centerX + w * 0.02f, h * 0.68f,
            centerX, sproutTopY
        )
    }
    val stemBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFA5D6A7), Color(0xFF43A047)),
        startY = sproutTopY,
        endY = groundY
    )
    drawPath(
        path = stemPath,
        brush = stemBrush,
        style = Stroke(width = w * 0.032f)
    )

    // Left Leaf with gradient and organic curve
    val leftLeafPath = Path().apply {
        moveTo(centerX, sproutTopY)
        cubicTo(
            centerX - w * 0.08f, sproutTopY - h * 0.08f,
            centerX - w * 0.18f, sproutTopY - h * 0.03f,
            centerX - w * 0.17f, sproutTopY + h * 0.02f
        )
        cubicTo(
            centerX - w * 0.10f, sproutTopY + h * 0.05f,
            centerX - w * 0.03f, sproutTopY + h * 0.02f,
            centerX, sproutTopY
        )
        close()
    }
    val leftLeafBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)),
        center = Offset(centerX - w * 0.08f, sproutTopY),
        radius = w * 0.15f
    )
    drawPath(path = leftLeafPath, brush = leftLeafBrush, style = Fill)

    // Right Leaf with lighter soft gradient
    val rightLeafPath = Path().apply {
        moveTo(centerX, sproutTopY)
        cubicTo(
            centerX + w * 0.08f, sproutTopY - h * 0.09f,
            centerX + w * 0.19f, sproutTopY - h * 0.04f,
            centerX + w * 0.18f, sproutTopY + h * 0.02f
        )
        cubicTo(
            centerX + w * 0.10f, sproutTopY + h * 0.05f,
            centerX + w * 0.03f, sproutTopY + h * 0.02f,
            centerX, sproutTopY
        )
        close()
    }
    val rightLeafBrush = Brush.radialGradient(
        colors = listOf(Color(0xFF81C784), Color(0xFF388E3C)),
        center = Offset(centerX + w * 0.08f, sproutTopY),
        radius = w * 0.15f
    )
    drawPath(path = rightLeafPath, brush = rightLeafBrush, style = Fill)

    // Sparkling dewdrop
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFF80D8FF)),
            center = Offset(centerX + w * 0.05f, sproutTopY - h * 0.03f),
            radius = w * 0.022f
        ),
        radius = w * 0.020f,
        center = Offset(centerX + w * 0.05f, sproutTopY - h * 0.03f)
    )
}

private fun DrawScope.drawSapling(w: Float, h: Float) {
    val centerX = w * 0.5f
    val groundY = h * 0.84f
    val topY = h * 0.44f

    // Trunk/stalk with subtle wood gradient
    val trunkPath = Path().apply {
        moveTo(centerX - w * 0.03f, groundY)
        lineTo(centerX + w * 0.03f, groundY)
        lineTo(centerX + w * 0.015f, topY)
        lineTo(centerX - w * 0.015f, topY)
        close()
    }
    val trunkBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF8D6E63), Color(0xFF4E342E)),
        startY = topY,
        endY = groundY
    )
    drawPath(trunkPath, brush = trunkBrush, style = Fill)

    // Side leaves along the stalk with gradient depth
    fun drawOrganicLeaf(startX: Float, startY: Float, tipX: Float, tipY: Float, isLight: Boolean) {
        val leafPath = Path().apply {
            moveTo(startX, startY)
            quadraticBezierTo((startX + tipX) / 2f + (if (isLight) 8f else -8f), startY - 10f, tipX, tipY)
            quadraticBezierTo((startX + tipX) / 2f, startY + 10f, startX, startY)
            close()
        }
        val colors = if (isLight) {
            listOf(Color(0xFF81C784), Color(0xFF388E3C))
        } else {
            listOf(Color(0xFF4CAF50), Color(0xFF1B5E20))
        }
        drawPath(
            path = leafPath,
            brush = Brush.radialGradient(colors = colors, center = Offset((startX + tipX) / 2f, (startY + tipY) / 2f), radius = w * 0.14f),
            style = Fill
        )
    }

    // Tier 1 leaves (low)
    drawOrganicLeaf(centerX, h * 0.68f, centerX - w * 0.18f, h * 0.64f, isLight = false)
    drawOrganicLeaf(centerX, h * 0.65f, centerX + w * 0.18f, h * 0.61f, isLight = true)

    // Tier 2 leaves (mid)
    drawOrganicLeaf(centerX, h * 0.56f, centerX - w * 0.20f, h * 0.51f, isLight = true)
    drawOrganicLeaf(centerX, h * 0.53f, centerX + w * 0.20f, h * 0.48f, isLight = false)

    // Top crown with radial gradients
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF43A047), Color(0xFF1B5E20)),
            center = Offset(centerX, topY + h * 0.02f),
            radius = w * 0.12f
        ),
        radius = w * 0.09f,
        center = Offset(centerX, topY + h * 0.02f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF81C784), Color(0xFF2E7D32)),
            center = Offset(centerX, topY - h * 0.02f),
            radius = w * 0.10f
        ),
        radius = w * 0.075f,
        center = Offset(centerX, topY - h * 0.02f)
    )
}

private fun DrawScope.drawYoungTree(w: Float, h: Float) {
    val centerX = w * 0.5f
    val groundY = h * 0.84f

    // Trunk
    val trunkPath = Path().apply {
        moveTo(centerX - w * 0.06f, groundY)
        lineTo(centerX + w * 0.06f, groundY)
        lineTo(centerX + w * 0.03f, h * 0.46f)
        lineTo(centerX - w * 0.03f, h * 0.46f)
        close()
    }
    val trunkBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF6D4C41), Color(0xFF3E2723)),
        startY = h * 0.46f,
        endY = groundY
    )
    drawPath(trunkPath, brush = trunkBrush, style = Fill)

    // Branches
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.55f),
        end = Offset(centerX - w * 0.16f, h * 0.45f),
        strokeWidth = w * 0.028f
    )
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.52f),
        end = Offset(centerX + w * 0.16f, h * 0.43f),
        strokeWidth = w * 0.028f
    )

    // Multi-layered lush canopy with radial depth
    val canopyCircles = listOf(
        Triple(Offset(centerX - w * 0.14f, h * 0.40f), w * 0.18f, listOf(Color(0xFF2E7D32), Color(0xFF0D5316))),
        Triple(Offset(centerX + w * 0.14f, h * 0.38f), w * 0.18f, listOf(Color(0xFF388E3C), Color(0xFF1B5E20))),
        Triple(Offset(centerX, h * 0.30f), w * 0.22f, listOf(Color(0xFF4CAF50), Color(0xFF1B5E20))),
        Triple(Offset(centerX - w * 0.06f, h * 0.24f), w * 0.16f, listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))),
        Triple(Offset(centerX + w * 0.06f, h * 0.22f), w * 0.15f, listOf(Color(0xFF81C784), Color(0xFF388E3C)))
    )

    for ((center, radius, colors) in canopyCircles) {
        drawCircle(
            brush = Brush.radialGradient(colors = colors, center = center, radius = radius),
            radius = radius,
            center = center
        )
    }
}

private fun DrawScope.drawBloomingTree(w: Float, h: Float) {
    val centerX = w * 0.5f
    val groundY = h * 0.84f

    // Sturdy Trunk with bark gradient
    val trunkPath = Path().apply {
        moveTo(centerX - w * 0.08f, groundY)
        lineTo(centerX + w * 0.08f, groundY)
        lineTo(centerX + w * 0.04f, h * 0.45f)
        lineTo(centerX - w * 0.04f, h * 0.45f)
        close()
    }
    val trunkBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF5D4037), Color(0xFF2E1C14)),
        startY = h * 0.45f,
        endY = groundY
    )
    drawPath(trunkPath, brush = trunkBrush, style = Fill)

    // Branches
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.52f),
        end = Offset(centerX - w * 0.20f, h * 0.38f),
        strokeWidth = w * 0.035f
    )
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.49f),
        end = Offset(centerX + w * 0.20f, h * 0.36f),
        strokeWidth = w * 0.035f
    )

    // Rich canopy clouds
    val canopyLayers = listOf(
        Triple(Offset(centerX - w * 0.18f, h * 0.40f), w * 0.21f, listOf(Color(0xFF2E7D32), Color(0xFF0D5316))),
        Triple(Offset(centerX + w * 0.18f, h * 0.38f), w * 0.21f, listOf(Color(0xFF388E3C), Color(0xFF1B5E20))),
        Triple(Offset(centerX, h * 0.32f), w * 0.24f, listOf(Color(0xFF43A047), Color(0xFF1B5E20))),
        Triple(Offset(centerX - w * 0.10f, h * 0.24f), w * 0.19f, listOf(Color(0xFF66BB6A), Color(0xFF2E7D32))),
        Triple(Offset(centerX + w * 0.10f, h * 0.22f), w * 0.19f, listOf(Color(0xFF81C784), Color(0xFF388E3C))),
        Triple(Offset(centerX, h * 0.16f), w * 0.15f, listOf(Color(0xFFA5D6A7), Color(0xFF4CAF50)))
    )

    for ((center, radius, colors) in canopyLayers) {
        drawCircle(
            brush = Brush.radialGradient(colors = colors, center = center, radius = radius),
            radius = radius,
            center = center
        )
    }

    // Blossoms / Flowers with petal highlights
    fun drawFlower(center: Offset, petalColor: Color, centerColor: Color, radius: Float) {
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(Color.White, petalColor), center = center, radius = radius),
            radius = radius,
            center = center
        )
        drawCircle(color = centerColor, radius = radius * 0.4f, center = center)
    }

    val pink = Color(0xFFF48FB1)
    val gold = Color(0xFFFFD54F)
    val softRose = Color(0xFFFF80AB)

    drawFlower(Offset(centerX - w * 0.15f, h * 0.37f), pink, gold, w * 0.030f)
    drawFlower(Offset(centerX + w * 0.16f, h * 0.35f), softRose, gold, w * 0.028f)
    drawFlower(Offset(centerX - w * 0.07f, h * 0.26f), pink, gold, w * 0.032f)
    drawFlower(Offset(centerX + w * 0.13f, h * 0.23f), softRose, Color.White, w * 0.029f)
    drawFlower(Offset(centerX, h * 0.19f), pink, gold, w * 0.031f)
    drawFlower(Offset(centerX + w * 0.03f, h * 0.32f), softRose, gold, w * 0.028f)
    drawFlower(Offset(centerX - w * 0.20f, h * 0.44f), pink, Color.White, w * 0.027f)
    drawFlower(Offset(centerX + w * 0.21f, h * 0.42f), pink, gold, w * 0.027f)
}

private fun DrawScope.drawMatureTree(w: Float, h: Float) {
    val centerX = w * 0.5f
    val groundY = h * 0.84f

    // Broad ancient oak trunk with flared roots
    val trunkPath = Path().apply {
        moveTo(centerX - w * 0.14f, groundY)
        quadraticBezierTo(centerX - w * 0.07f, h * 0.68f, centerX - w * 0.06f, h * 0.44f)
        lineTo(centerX + w * 0.06f, h * 0.44f)
        quadraticBezierTo(centerX + w * 0.07f, h * 0.68f, centerX + w * 0.14f, groundY)
        close()
    }
    val trunkBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF4E342E), Color(0xFF1B0000)),
        startY = h * 0.44f,
        endY = groundY
    )
    drawPath(trunkPath, brush = trunkBrush, style = Fill)

    // Majestic spreading branches
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.50f),
        end = Offset(centerX - w * 0.26f, h * 0.38f),
        strokeWidth = w * 0.045f
    )
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.48f),
        end = Offset(centerX + w * 0.26f, h * 0.36f),
        strokeWidth = w * 0.045f
    )
    drawLine(
        brush = trunkBrush,
        start = Offset(centerX, h * 0.42f),
        end = Offset(centerX, h * 0.28f),
        strokeWidth = w * 0.035f
    )

    // Grand layered canopy with immense volume and lighting
    val matureCanopy = listOf(
        Triple(Offset(centerX - w * 0.24f, h * 0.40f), w * 0.25f, listOf(Color(0xFF1B5E20), Color(0xFF052B0A))),
        Triple(Offset(centerX + w * 0.24f, h * 0.38f), w * 0.25f, listOf(Color(0xFF2E7D32), Color(0xFF0D5316))),
        Triple(Offset(centerX, h * 0.30f), w * 0.29f, listOf(Color(0xFF388E3C), Color(0xFF0D5316))),
        Triple(Offset(centerX - w * 0.15f, h * 0.22f), w * 0.25f, listOf(Color(0xFF43A047), Color(0xFF1B5E20))),
        Triple(Offset(centerX + w * 0.15f, h * 0.20f), w * 0.25f, listOf(Color(0xFF4CAF50), Color(0xFF2E7D32))),
        Triple(Offset(centerX, h * 0.14f), w * 0.22f, listOf(Color(0xFF66BB6A), Color(0xFF388E3C))),
        Triple(Offset(centerX - w * 0.06f, h * 0.11f), w * 0.15f, listOf(Color(0xFF81C784), Color(0xFF43A047)))
    )

    for ((center, radius, colors) in matureCanopy) {
        drawCircle(
            brush = Brush.radialGradient(colors = colors, center = center, radius = radius),
            radius = radius,
            center = center
        )
    }

    // Dappled golden sunlight fruits & glowing highlights
    fun drawGlowingFruit(center: Offset, radius: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD54F), Color(0xFFFFA000)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }

    drawGlowingFruit(Offset(centerX - w * 0.22f, h * 0.35f), w * 0.026f)
    drawGlowingFruit(Offset(centerX + w * 0.23f, h * 0.33f), w * 0.028f)
    drawGlowingFruit(Offset(centerX - w * 0.10f, h * 0.20f), w * 0.030f)
    drawGlowingFruit(Offset(centerX + w * 0.14f, h * 0.16f), w * 0.027f)
    drawGlowingFruit(Offset(centerX, h * 0.09f), w * 0.028f)
    drawGlowingFruit(Offset(centerX + w * 0.04f, h * 0.25f), w * 0.025f)
}
