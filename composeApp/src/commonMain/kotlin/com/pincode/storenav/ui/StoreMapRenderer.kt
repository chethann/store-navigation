package com.pincode.storenav.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pincode.storenav.model.*
import kotlin.math.abs
import kotlin.math.min

fun DrawScope.drawPoints(points: List<Point>) {
    if (points.isEmpty()) return

    // Draw points
    points.forEach { point ->
        drawCircle(
            color = Color.Blue,
            radius = 5f,
            center = Offset(point.x, point.y)
        )
    }

    // Draw lines connecting points
    if (points.size > 1) {
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color.Blue,
                start = Offset(points[i].x, points[i].y),
                end = Offset(points[i + 1].x, points[i + 1].y),
                strokeWidth = 2f
            )
        }

        // Connect last point to first point if there are at least 3 points
        if (points.size >= 3) {
            drawLine(
                color = Color.Blue,
                start = Offset(points.last().x, points.last().y),
                end = Offset(points.first().x, points.first().y),
                strokeWidth = 2f
            )
        }
    }
}

fun DrawScope.drawStoreMap(map: StoreMap) {
    // Draw floor
    drawFloor(map.floor)
    
    // Draw aisles
    map.floor.aisles.forEach { aisle ->
        drawAisle(aisle)
    }
}

private fun DrawScope.drawFloor(floor: StoreFloor) {
    val points = floor.vertices
    if (points.isEmpty()) return

    // Draw floor outline
    for (i in points.indices) {
        val start = points[i]
        val end = points[(i + 1) % points.size]
        drawLine(
            color = Color.Gray,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = 3f
        )
    }
}

private fun DrawScope.drawAisle(aisle: Aisle) {
    // Draw aisle outline
    drawRect(
        color = Color.DarkGray,
        topLeft = Offset(aisle.position.x, aisle.position.y),
        size = Size(aisle.width, aisle.length),
        style = Stroke(width = 2f)
    )

    val spacing = 4f
    val isHorizontal = aisle.length > aisle.width  // Determine orientation based on dimensions

    if (isHorizontal) {
        // Horizontal layout (length > width)
        val shelfAreaWidth = (aisle.width - spacing) / 2

        // Draw side one shelves (left side)
        var currentY = aisle.position.y + spacing
        val totalWeightSideOne = aisle.sideOneShelves.sumOf { it.weight.toDouble() }.toFloat()
        val availableHeightSideOne = aisle.length - (spacing * (aisle.sideOneShelves.size + 1))

        aisle.sideOneShelves.forEach { shelf ->
            // Calculate shelf height based on weight and available space
            val shelfHeight = if (totalWeightSideOne > 0) {
                (shelf.weight / totalWeightSideOne) * availableHeightSideOne
            } else {
                0f
            }

            // Draw the shelf
            val shelfLeft = aisle.position.x + spacing
            val shelfWidth = shelfAreaWidth - spacing
            
            drawRect(
                color = Color.Red,
                topLeft = Offset(shelfLeft, currentY),
                size = Size(shelfWidth, shelfHeight),
                style = Stroke(width = 1.5f)
            )

            // Draw rows within the shelf - rows should be vertical for vertical shelves
            if (shelf.rows.isNotEmpty()) {
                val rowSpacing = 2f
                val rowAreaWidth = shelfWidth - 4f // Small margin on left and right
                val rowWidth = (rowAreaWidth - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                
                shelf.rows.forEachIndexed { index, row ->
                    val rowX = shelfLeft + 2f + (index * (rowWidth + rowSpacing))
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(rowX, currentY + 2f),
                        size = Size(rowWidth, shelfHeight - 4f),
                        style = Stroke(width = 1f)
                    )
                }
            }

            currentY += shelfHeight + spacing
        }

        // Draw side two shelves (right side)
        currentY = aisle.position.y + spacing
        val totalWeightSideTwo = aisle.sideTwoShelves.sumOf { it.weight.toDouble() }.toFloat()
        val availableHeightSideTwo = aisle.length - (spacing * (aisle.sideTwoShelves.size + 1))

        aisle.sideTwoShelves.forEach { shelf ->
            // Calculate shelf height based on weight and available space
            val shelfHeight = if (totalWeightSideTwo > 0) {
                (shelf.weight / totalWeightSideTwo) * availableHeightSideTwo
            } else {
                0f
            }

            // Draw the shelf
            val shelfLeft = aisle.position.x + shelfAreaWidth + spacing
            val shelfWidth = shelfAreaWidth - spacing
            
            drawRect(
                color = Color.Green,
                topLeft = Offset(shelfLeft, currentY),
                size = Size(shelfWidth, shelfHeight),
                style = Stroke(width = 1.5f)
            )

            // Draw rows within the shelf - rows should be vertical for vertical shelves
            if (shelf.rows.isNotEmpty()) {
                val rowSpacing = 2f
                val rowAreaWidth = shelfWidth - 4f // Small margin on left and right
                val rowWidth = (rowAreaWidth - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                
                shelf.rows.forEachIndexed { index, row ->
                    val rowX = shelfLeft + 2f + (index * (rowWidth + rowSpacing))
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(rowX, currentY + 2f),
                        size = Size(rowWidth, shelfHeight - 4f),
                        style = Stroke(width = 1f)
                    )
                }
            }

            currentY += shelfHeight + spacing
        }
    } else {
        // Vertical layout (width > length)
        val shelfAreaHeight = (aisle.length - spacing) / 2

        // Draw side one shelves (top side)
        var currentX = aisle.position.x + spacing
        val totalWeightSideOne = aisle.sideOneShelves.sumOf { it.weight.toDouble() }.toFloat()
        val availableWidthSideOne = aisle.width - (spacing * (aisle.sideOneShelves.size + 1))

        aisle.sideOneShelves.forEach { shelf ->
            // Calculate shelf width based on weight and available space
            val shelfWidth = if (totalWeightSideOne > 0) {
                (shelf.weight / totalWeightSideOne) * availableWidthSideOne
            } else {
                0f
            }

            // Draw the shelf
            val shelfTop = aisle.position.y + spacing
            val shelfHeight = shelfAreaHeight - spacing
            
            drawRect(
                color = Color.Red,
                topLeft = Offset(currentX, shelfTop),
                size = Size(shelfWidth, shelfHeight),
                style = Stroke(width = 1.5f)
            )

            // Draw rows within the shelf - rows should be horizontal for horizontal shelves
            if (shelf.rows.isNotEmpty()) {
                val rowSpacing = 2f
                val rowAreaHeight = shelfHeight - 4f // Small margin on top and bottom
                val rowHeight = (rowAreaHeight - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                
                shelf.rows.forEachIndexed { index, row ->
                    val rowY = shelfTop + 2f + (index * (rowHeight + rowSpacing))
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(currentX + 2f, rowY),
                        size = Size(shelfWidth - 4f, rowHeight),
                        style = Stroke(width = 1f)
                    )
                }
            }

            currentX += shelfWidth + spacing
        }

        // Draw side two shelves (bottom side)
        currentX = aisle.position.x + spacing
        val totalWeightSideTwo = aisle.sideTwoShelves.sumOf { it.weight.toDouble() }.toFloat()
        val availableWidthSideTwo = aisle.width - (spacing * (aisle.sideTwoShelves.size + 1))

        aisle.sideTwoShelves.forEach { shelf ->
            // Calculate shelf width based on weight and available space
            val shelfWidth = if (totalWeightSideTwo > 0) {
                (shelf.weight / totalWeightSideTwo) * availableWidthSideTwo
            } else {
                0f
            }

            // Draw the shelf
            val shelfTop = aisle.position.y + shelfAreaHeight + spacing
            val shelfHeight = shelfAreaHeight - spacing
            
            drawRect(
                color = Color.Green,
                topLeft = Offset(currentX, shelfTop),
                size = Size(shelfWidth, shelfHeight),
                style = Stroke(width = 1.5f)
            )

            // Draw rows within the shelf - rows should be horizontal for horizontal shelves
            if (shelf.rows.isNotEmpty()) {
                val rowSpacing = 2f
                val rowAreaHeight = shelfHeight - 4f // Small margin on top and bottom
                val rowHeight = (rowAreaHeight - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                
                shelf.rows.forEachIndexed { index, row ->
                    val rowY = shelfTop + 2f + (index * (rowHeight + rowSpacing))
                    drawRect(
                        color = Color.Blue,
                        topLeft = Offset(currentX + 2f, rowY),
                        size = Size(shelfWidth - 4f, rowHeight),
                        style = Stroke(width = 1f)
                    )
                }
            }

            currentX += shelfWidth + spacing
        }
    }
}

fun DrawScope.drawAislePreview(start: Offset, current: Offset, existingAisles: List<Aisle>, scale: Float) {
    val topLeft = Offset(
        x = min(start.x, current.x),
        y = min(start.y, current.y)
    )
    val size = Size(
        width = abs(current.x - start.x),
        height = abs(current.y - start.y)
    )

    // Create a rectangle for the preview aisle
    val previewRect = Rect(offset = topLeft, size = size)
    
    // Check for collisions with existing aisles
    val hasCollision = existingAisles.any { aisle ->
        val aisleRect = Rect(
            offset = Offset(
                x = (aisle.position.x * scale) + start.x,
                y = (aisle.position.y * scale) + start.y
            ),
            size = Size(
                width = aisle.width * scale,
                height = aisle.length * scale
            )
        )
        previewRect.overlaps(aisleRect)
    }

    // Draw semi-transparent fill
    drawRect(
        color = if (hasCollision) Color.Red.copy(alpha = 0.2f) else Color.Blue.copy(alpha = 0.2f),
        topLeft = topLeft,
        size = size
    )
    
    // Draw border
    drawRect(
        color = if (hasCollision) Color.Red.copy(alpha = 0.6f) else Color.Blue.copy(alpha = 0.6f),
        topLeft = topLeft,
        size = size,
        style = Stroke(width = 2f)
    )
    
    // Draw corner points
    val corners = listOf(
        topLeft,
        topLeft.copy(x = topLeft.x + size.width),
        topLeft.copy(y = topLeft.y + size.height),
        topLeft.copy(x = topLeft.x + size.width, y = topLeft.y + size.height)
    )
    
    corners.forEach { corner ->
        drawCircle(
            color = if (hasCollision) Color.Red.copy(alpha = 0.8f) else Color.Blue.copy(alpha = 0.8f),
            radius = 4f,
            center = corner
        )
    }
    
    // Draw dimensions
    val dimensionColor = if (hasCollision) Color.Red.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)
    val padding = 10f
    
    // Width dimension
    drawLine(
        color = dimensionColor,
        start = topLeft.copy(y = topLeft.y - padding),
        end = topLeft.copy(x = topLeft.x + size.width, y = topLeft.y - padding),
        strokeWidth = 1f
    )
    
    // Height dimension
    drawLine(
        color = dimensionColor,
        start = topLeft.copy(x = topLeft.x - padding),
        end = topLeft.copy(x = topLeft.x - padding, y = topLeft.y + size.height),
        strokeWidth = 1f
    )
}
