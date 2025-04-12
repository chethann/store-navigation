package com.pincode.storenav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentRows by remember(shelf) { mutableStateOf(shelf.rows) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDrawingRow by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }

    // Center the shelf in the view initially
    LaunchedEffect(Unit) {
        offset = Offset(100f, 100f)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Shelf Editor") },
            navigationIcon = {
                Button(onClick = onBack) {
                    Text("Back")
                }
            },
            actions = {
                Button(
                    onClick = { isDrawingRow = !isDrawingRow },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (isDrawingRow) "Cancel Row" else "Add Row")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (!isDrawingRow) {
                                scale *= zoom
                                offset += pan
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position ->
                                if (isDrawingRow) {
                                    startPoint = position
                                    currentPoint = position
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDrawingRow) {
                                    currentPoint = change.position
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (isDrawingRow && startPoint != null && currentPoint != null) {
                                    val newRow = createRow(
                                        start = startPoint!!,
                                        end = currentPoint!!,
                                        offset = offset,
                                        scale = scale
                                    )
                                    
                                    // Only add row if it's within shelf bounds
                                    if (isRowWithinShelf(newRow, shelf)) {
                                        // Update local state first
                                        currentRows = currentRows + newRow
                                        // Then update parent with all rows
                                        onShelfUpdate(shelf.copy(rows = currentRows))
                                    }

                                    startPoint = null
                                    currentPoint = null
                                    isDrawingRow = false
                                }
                            },
                            onDragCancel = {
                                startPoint = null
                                currentPoint = null
                            }
                        )
                    }
            ) {
                scale(scale) {
                    translate(offset.x, offset.y) {
                        // Draw shelf outline
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(
                                shelf.width,
                                shelf.length
                            ),
                            style = Stroke(width = 2f)
                        )

                        // Draw all rows
                        currentRows.forEach { row ->
                            drawRect(
                                color = Color.Blue,
                                topLeft = Offset(row.position.x, row.position.y),
                                size = androidx.compose.ui.geometry.Size(
                                    row.width,
                                    row.length
                                ),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }

                // Draw row preview while dragging
                if (isDrawingRow && startPoint != null && currentPoint != null) {
                    drawRowPreview(
                        start = startPoint!!,
                        current = currentPoint!!,
                        scale = scale,
                        offset = offset,
                        shelf = shelf
                    )
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
} 