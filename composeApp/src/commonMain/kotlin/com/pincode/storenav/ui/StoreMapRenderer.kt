package com.pincode.storenav.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.pincode.storenav.model.*
import com.pincode.storenav.util.PathFinder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

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

// Update function signature to use WaypointData throughout
fun DrawScope.drawNavigationPath(waypoints: List<PathFinder.WaypointData>?) {
    if (waypoints.isNullOrEmpty()) return
    
    // Extract just the points for convenience
    val path = waypoints.map { it.point }
    
    // Draw path lines
    for (i in 0 until path.size - 1) {
        val start = path[i]
        val end = path[i + 1]
        
        drawLine(
            color = Color.Magenta,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
        )
    }
    
    // Draw regular waypoints
    waypoints.forEach { waypoint ->
        when (waypoint.type) {
            PathFinder.WaypointType.REGULAR -> {
                // Regular waypoint (smaller, less prominent)
                drawCircle(
                    color = Color.Magenta.copy(alpha = 0.6f),
                    radius = 5f,
                    center = Offset(waypoint.point.x, waypoint.point.y)
                )
            }
            
            PathFinder.WaypointType.SHELF_DESTINATION -> {
                // Shelf destination (with number)
                val number = waypoint.number ?: 0
                
                // Draw outer circle
                drawCircle(
                    color = Color.Yellow,
                    radius = 15f,
                    center = Offset(waypoint.point.x, waypoint.point.y)
                )
                
                // Draw inner circle
                drawCircle(
                    color = Color.Black,
                    radius = 12f,
                    center = Offset(waypoint.point.x, waypoint.point.y)
                )
                
                // Draw the number using our cross-platform method
                val numberPath = createNumberPath(number.toString(), waypoint.point.x, waypoint.point.y)
                drawPath(
                    path = numberPath,
                    color = Color.White,
                    style = Stroke(width = 2f)
                )
            }
            
            PathFinder.WaypointType.START -> {
                // Special start marker
                drawCircle(
                    color = Color.Green,
                    radius = 10f,
                    center = Offset(waypoint.point.x, waypoint.point.y)
                )
                
                // Draw "S" shape
                val path = androidx.compose.ui.graphics.Path().apply {
                    // Simple "S" shape
                    moveTo(waypoint.point.x - 5, waypoint.point.y - 5)
                    cubicTo(
                        waypoint.point.x - 5, waypoint.point.y - 8,
                        waypoint.point.x + 5, waypoint.point.y - 2,
                        waypoint.point.x + 5, waypoint.point.y
                    )
                    cubicTo(
                        waypoint.point.x + 5, waypoint.point.y + 3,
                        waypoint.point.x - 5, waypoint.point.y + 2,
                        waypoint.point.x - 5, waypoint.point.y + 5
                    )
                }
                
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2f)
                )
            }
            
            PathFinder.WaypointType.END -> {
                // End marker
                drawCircle(
                    color = Color.Blue,
                    radius = 10f,
                    center = Offset(waypoint.point.x, waypoint.point.y)
                )
                
                // Draw "E" shape or marker
                val path = androidx.compose.ui.graphics.Path().apply {
                    // Simple "E" shape
                    moveTo(waypoint.point.x - 5, waypoint.point.y - 5)
                    lineTo(waypoint.point.x + 5, waypoint.point.y - 5)
                    moveTo(waypoint.point.x - 5, waypoint.point.y)
                    lineTo(waypoint.point.x + 5, waypoint.point.y)
                    moveTo(waypoint.point.x - 5, waypoint.point.y + 5)
                    lineTo(waypoint.point.x + 5, waypoint.point.y + 5)
                    moveTo(waypoint.point.x - 5, waypoint.point.y - 5)
                    lineTo(waypoint.point.x - 5, waypoint.point.y + 5)
                }
                
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

// Helper function to create number paths in a cross-platform way
private fun createNumberPath(number: String, x: Float, y: Float): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    
    // Adjust positioning to center the number
    val centerX = x
    val centerY = y
    val size = 8f  // Size of the number paths
    
    when (number) {
        "1" -> {
            // Draw a simple "1" as a vertical line
            path.moveTo(centerX, centerY - size)
            path.lineTo(centerX, centerY + size)
        }
        "2" -> {
            // Draw a simple "2" shape
            path.moveTo(centerX - size, centerY - size)
            path.lineTo(centerX + size, centerY - size)
            path.lineTo(centerX + size, centerY)
            path.lineTo(centerX - size, centerY)
            path.lineTo(centerX - size, centerY + size)
            path.lineTo(centerX + size, centerY + size)
        }
        "3" -> {
            // Draw a simple "3" shape
            path.moveTo(centerX - size, centerY - size)
            path.lineTo(centerX + size, centerY - size)
            path.lineTo(centerX + size, centerY)
            path.lineTo(centerX - size, centerY)
            path.moveTo(centerX + size, centerY)
            path.lineTo(centerX + size, centerY + size)
            path.lineTo(centerX - size, centerY + size)
        }
        "4" -> {
            // Draw a simple "4" shape
            path.moveTo(centerX - size, centerY - size)
            path.lineTo(centerX - size, centerY)
            path.lineTo(centerX + size, centerY)
            path.moveTo(centerX + size, centerY - size)
            path.lineTo(centerX + size, centerY + size)
        }
        "5" -> {
            // Draw a simple "5" shape
            path.moveTo(centerX + size, centerY - size)
            path.lineTo(centerX - size, centerY - size)
            path.lineTo(centerX - size, centerY)
            path.lineTo(centerX + size, centerY)
            path.lineTo(centerX + size, centerY + size)
            path.lineTo(centerX - size, centerY + size)
        }
        "6" -> {
            // Draw a simple "6" shape
            path.moveTo(centerX + size, centerY - size)
            path.lineTo(centerX - size, centerY - size)
            path.lineTo(centerX - size, centerY + size)
            path.lineTo(centerX + size, centerY + size)
            path.lineTo(centerX + size, centerY)
            path.lineTo(centerX - size, centerY)
        }
        "7" -> {
            // Draw a simple "7" shape
            path.moveTo(centerX - size, centerY - size)
            path.lineTo(centerX + size, centerY - size)
            path.lineTo(centerX, centerY + size)
        }
        "8" -> {
            // Draw a simple "8" shape - two circles
            path.addOval(androidx.compose.ui.geometry.Rect(
                left = centerX - size/2, 
                top = centerY - size, 
                right = centerX + size/2, 
                bottom = centerY
            ))
            path.addOval(androidx.compose.ui.geometry.Rect(
                left = centerX - size/2, 
                top = centerY, 
                right = centerX + size/2, 
                bottom = centerY + size
            ))
        }
        "9" -> {
            // Draw a simple "9" shape
            path.moveTo(centerX - size, centerY + size)
            path.lineTo(centerX + size, centerY + size)
            path.lineTo(centerX + size, centerY - size)
            path.lineTo(centerX - size, centerY - size)
            path.lineTo(centerX - size, centerY)
            path.lineTo(centerX + size, centerY)
        }
        else -> {
            // For other numbers or multi-digit numbers, draw a circle with dots
            path.addOval(androidx.compose.ui.geometry.Rect(
                left = centerX - size, 
                top = centerY - size, 
                right = centerX + size, 
                bottom = centerY + size
            ))
            
            // Add dots in the center
            val dotSize = size / 4
            path.addOval(androidx.compose.ui.geometry.Rect(
                left = centerX - dotSize, 
                top = centerY - dotSize, 
                right = centerX + dotSize, 
                bottom = centerY + dotSize
            ))
        }
    }
    
    return path
}

private fun distanceBetween(p1: Point, p2: Point): Float {
    return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
}
