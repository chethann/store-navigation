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

    // Draw shelves within aisle
    aisle.shelves.forEach { shelf ->
        val shelfPos = Offset(
            x = aisle.position.x + shelf.position.x,
            y = aisle.position.y + shelf.position.y
        )
        
        // Draw shelf outline
        drawRect(
            color = Color.Red,
            topLeft = shelfPos,
            size = Size(shelf.width, shelf.length),
            style = Stroke(width = 1.5f)
        )

        // Draw rows within shelf
        shelf.rows.forEach { row ->
            val rowPos = Offset(
                x = shelfPos.x + row.position.x,
                y = shelfPos.y + row.position.y
            )
            
            // Draw row outline
            drawRect(
                color = Color.Blue,
                topLeft = rowPos,
                size = Size(row.width, row.length),
                style = Stroke(width = 1f)
            )
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
