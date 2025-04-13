package com.pincode.storenav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pincode.storenav.model.Point
import com.pincode.storenav.model.Shelf
import com.pincode.storenav.model.ShelfRow
import java.util.*
import kotlin.math.abs

@Composable
fun ShelfEditor(
    shelf: Shelf,
    onShelfUpdate: (Shelf) -> Unit,
    numRowsToAdd: String,
    onNumRowsToAddChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isParentAisleHorizontal: Boolean = true // Add parent aisle orientation parameter
) {
    var currentRows by remember(shelf) { mutableStateOf(shelf.rows) }
    var scale by remember { mutableStateOf(4f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Estimate shelf dimensions based on weight and orientation
    val (estimatedWidth, estimatedHeight) = if (isParentAisleHorizontal) {
        // For horizontal aisles, shelves are along the sides (vertical orientation)
        50f to (shelf.weight * 30f)
    } else {
        // For vertical aisles, shelves are at top/bottom (horizontal orientation)
        (shelf.weight * 30f) to 50f
    }

    // Center the shelf in the view initially
    LaunchedEffect(Unit) {
        offset = Offset(100f, 100f)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Shelf Editor (Weight: ${shelf.weight})") },
            navigationIcon = {
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        )

        // Add row controls
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = numRowsToAdd,
                onValueChange = onNumRowsToAddChange,
                label = { Text("Number of Rows") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val rowCount = numRowsToAdd.toIntOrNull() ?: return@Button
                if (rowCount <= 0) return@Button
                
                // Calculate row dimensions based on orientation
                val rowSpacing = 2f
                
                val newRows = if (isParentAisleHorizontal) {
                    // For horizontal aisles (shelves on left/right)
                    // Rows should be VERTICAL (running parallel to the aisle)
                    val rowWidth = (estimatedWidth - ((rowCount - 1) * rowSpacing)) / rowCount
                    
                    (0 until rowCount).map { index ->
                        val xPosition = index * (rowWidth + rowSpacing)
                        ShelfRow(
                            id = UUID.randomUUID().toString(),
                            position = Point(x = xPosition, y = 2f),
                            width = rowWidth,
                            length = estimatedHeight - 4f, // Small margin on top/bottom
                            height = 15f
                        )
                    }
                } else {
                    // For vertical aisles (shelves on top/bottom)
                    // Rows should be HORIZONTAL (running parallel to the aisle)
                    val rowHeight = (estimatedHeight - ((rowCount - 1) * rowSpacing)) / rowCount
                    
                    (0 until rowCount).map { index ->
                        val yPosition = index * (rowHeight + rowSpacing)
                        ShelfRow(
                            id = UUID.randomUUID().toString(),
                            position = Point(x = 2f, y = yPosition),
                            width = estimatedWidth - 4f, // Small margin on sides
                            length = rowHeight,
                            height = 15f
                        )
                    }
                }
                
                // Update the shelf with new rows
                onShelfUpdate(shelf.copy(rows = newRows))
                currentRows = newRows
            }) {
                Text("Generate Rows")
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            offset += pan
                        }
                    }
            ) {
                withTransform({
                    translate(offset.x, offset.y)
                    //scale(scale)
                }) {
                    // Draw shelf outline based on estimated dimensions
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(0f, 0f),
                        size = Size(estimatedWidth, estimatedHeight),
                        style = Stroke(width = 2f)
                    )

                    // Draw all rows
                    currentRows.forEach { row ->
                        drawRect(
                            color = Color.Blue,
                            topLeft = Offset(row.position.x, row.position.y),
                            size = Size(row.width, row.length),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }
    }
}

private fun createRow(
    start: Offset,
    end: Offset,
    offset: Offset,
    scale: Float
): ShelfRow {
    // Convert screen coordinates to shelf-relative coordinates
    val startPoint = Point(
        x = (start.x - offset.x) / scale,
        y = (start.y - offset.y) / scale
    )
    val endPoint = Point(
        x = (end.x - offset.x) / scale,
        y = (end.y - offset.y) / scale
    )

    val width = abs(endPoint.x - startPoint.x)
    val length = abs(endPoint.y - startPoint.y)
    
    val position = Point(
        x = minOf(startPoint.x, endPoint.x),
        y = minOf(startPoint.y, endPoint.y)
    )

    return ShelfRow(
        id = UUID.randomUUID().toString(),
        position = position,
        width = width,
        length = length,
        height = 30f
    )
}

/*
private fun isRowWithinShelf(row: ShelfRow, shelf: Shelf): Boolean {
    return row.position.x >= 0 &&
           row.position.y >= 0 &&
           row.position.x + row.width <= shelf.width &&
           row.position.y + row.length <= shelf.length
}

private fun DrawScope.drawRowPreview(
    start: Offset,
    current: Offset,
    scale: Float,
    offset: Offset,
    shelf: Shelf
) {
    // Convert screen coordinates to shelf-relative coordinates
    val startPoint = Point(
        x = (start.x - offset.x) / scale,
        y = (start.y - offset.y) / scale
    )
    val endPoint = Point(
        x = (current.x - offset.x) / scale,
        y = (current.y - offset.y) / scale
    )

    val topLeft = Offset(
        x = minOf(startPoint.x, endPoint.x),
        y = minOf(startPoint.y, endPoint.y)
    )
    val size = androidx.compose.ui.geometry.Size(
        width = abs(endPoint.x - startPoint.x),
        height = abs(endPoint.y - startPoint.y)
    )

    // Check if preview is within shelf bounds
    val isWithinBounds = topLeft.x >= 0 &&
                        topLeft.y >= 0 &&
                        topLeft.x + size.width <= shelf.width &&
                        topLeft.y + size.height <= shelf.length

    // Draw preview in the correct position relative to the shelf
    scale(scale) {
        translate(offset.x, offset.y) {
            drawRect(
                color = if (isWithinBounds) Color.Blue.copy(alpha = 0.3f) else Color.Blue.copy(alpha = 0.1f),
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 2f)
            )
        }
    }
} */
