package com.pincode.storenav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.pincode.storenav.model.Aisle
import com.pincode.storenav.model.Point
import com.pincode.storenav.model.Shelf
import java.util.*
import kotlin.math.abs

@Composable
fun AisleEditor(
    aisle: Aisle,
    onAisleUpdate: (Aisle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep track of all shelves locally
    var currentShelves by remember(aisle) { mutableStateOf(aisle.shelves) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDrawingShelf by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var selectedShelf by remember { mutableStateOf<Shelf?>(null) }

    if (selectedShelf != null) {
        ShelfEditor(
            shelf = selectedShelf!!,
            onShelfUpdate = { updatedShelf ->
                // Update both local state and parent
                currentShelves = currentShelves.map { shelf ->
                    if (shelf.id == updatedShelf.id) updatedShelf else shelf
                }
                onAisleUpdate(aisle.copy(shelves = currentShelves))
                selectedShelf = updatedShelf
            },
            onBack = { selectedShelf = null }
        )
        return
    }

    // Center the aisle in the view initially
    LaunchedEffect(Unit) {
        offset = Offset(100f, 100f)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Aisle Editor") },
            navigationIcon = {
                Button(onClick = onBack) {
                    Text("Back")
                }
            },
            actions = {
                Button(
                    onClick = { isDrawingShelf = !isDrawingShelf },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(if (isDrawingShelf) "Cancel Shelf" else "Add Shelf")
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (!isDrawingShelf) {
                                scale *= zoom
                                offset += pan
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            if (!isDrawingShelf) {
                                // Convert tap position to aisle coordinates
                                val tapPoint = Point(
                                    x = (position.x - offset.x) / scale,
                                    y = (position.y - offset.y) / scale
                                )
                                
                                // Find clicked shelf
                                selectedShelf = currentShelves.find { shelf ->
                                    val isXInBounds = tapPoint.x >= shelf.position.x && 
                                        tapPoint.x <= shelf.position.x + shelf.width
                                    val isYInBounds = tapPoint.y >= shelf.position.y && 
                                        tapPoint.y <= shelf.position.y + shelf.length
                                    isXInBounds && isYInBounds
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position ->
                                if (isDrawingShelf) {
                                    startPoint = position
                                    currentPoint = position
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDrawingShelf) {
                                    currentPoint = change.position
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (isDrawingShelf && startPoint != null && currentPoint != null) {
                                    val newShelf = createShelf(
                                        start = startPoint!!,
                                        end = currentPoint!!,
                                        offset = offset,
                                        scale = scale
                                    )
                                    
                                    // Only add shelf if it's within aisle bounds
                                    if (isShelfWithinAisle(newShelf, aisle)) {
                                        // Update local state first
                                        currentShelves = currentShelves + newShelf
                                        // Then update parent with all shelves
                                        onAisleUpdate(aisle.copy(shelves = currentShelves))
                                    }

                                    startPoint = null
                                    currentPoint = null
                                    isDrawingShelf = false
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
                        // Draw aisle outline
                        drawRect(
                            color = Color.Gray,
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(
                                aisle.width,
                                aisle.length
                            )
                        )

                        // Draw all shelves using the local state
                        currentShelves.forEach { shelf ->
                            // Draw shelf outline
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(shelf.position.x, shelf.position.y),
                                size = androidx.compose.ui.geometry.Size(
                                    shelf.width,
                                    shelf.length
                                ),
                                style = Stroke(width = 2f)
                            )

                            // Draw rows within shelf
                            shelf.rows.forEach { row ->
                                val rowPos = Offset(
                                    x = shelf.position.x + row.position.x,
                                    y = shelf.position.y + row.position.y
                                )
                                drawRect(
                                    color = Color.Blue,
                                    topLeft = rowPos,
                                    size = androidx.compose.ui.geometry.Size(
                                        row.width,
                                        row.length
                                    ),
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                    }
                }

                // Draw shelf preview while dragging
                if (isDrawingShelf && startPoint != null && currentPoint != null) {
                    drawShelfPreview(
                        start = startPoint!!,
                        current = currentPoint!!,
                        scale = scale,
                        offset = offset,
                        aisle = aisle
                    )
                }
            }
        }
    }
}

private fun createShelf(
    start: Offset,
    end: Offset,
    offset: Offset,
    scale: Float
): Shelf {
    // Convert screen coordinates to aisle-relative coordinates
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

    return Shelf(
        id = UUID.randomUUID().toString(),
        position = position,  // Position is now relative to aisle's top-left corner
        width = width,
        length = length,
        height = 180f
    )
}

private fun isShelfWithinAisle(shelf: Shelf, aisle: Aisle): Boolean {
    return shelf.position.x >= 0 &&
           shelf.position.y >= 0 &&
           shelf.position.x + shelf.width <= aisle.width &&
           shelf.position.y + shelf.length <= aisle.length
}

private fun DrawScope.drawShelfPreview(
    start: Offset,
    current: Offset,
    scale: Float,
    offset: Offset,
    aisle: Aisle
) {
    // Convert screen coordinates to aisle-relative coordinates
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

    // Check if preview is within aisle bounds
    val isWithinBounds = topLeft.x >= 0 &&
                        topLeft.y >= 0 &&
                        topLeft.x + size.width <= aisle.width &&
                        topLeft.y + size.height <= aisle.length

    // Draw preview in the correct position relative to the aisle
    scale(scale) {
        translate(offset.x, offset.y) {
            drawRect(
                color = if (isWithinBounds) Color.Red.copy(alpha = 0.3f) else Color.Red.copy(alpha = 0.1f),
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 2f)
            )
        }
    }
} 