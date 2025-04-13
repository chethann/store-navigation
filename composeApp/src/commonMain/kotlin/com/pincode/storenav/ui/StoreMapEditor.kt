package com.pincode.storenav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pincode.storenav.model.Aisle
import com.pincode.storenav.model.Point
import com.pincode.storenav.model.StoreFloor
import com.pincode.storenav.model.StoreMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import kotlin.math.abs

@Composable
fun StoreMapEditor(
    modifier: Modifier = Modifier
) {
    var storeMap by remember { mutableStateOf<StoreMap?>(null) }
    var isDrawingFloor by remember { mutableStateOf(false) }
    var isDrawingAisle by remember { mutableStateOf(false) }
    var floorPoints by remember { mutableStateOf(listOf<Point>()) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var hasCollision by remember { mutableStateOf(false) }
    var selectedAisle by remember { mutableStateOf<Aisle?>(null) }
    var scale by remember { mutableStateOf(1.5f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Let's add min and max zoom constraints
    val minScale = 0.5f
    val maxScale = 5.0f
    val moveStep = 10f


    // JSON formatter with pretty printing
    val json = remember { Json { prettyPrint = true } }

    val focusRequester = remember { FocusRequester() }



// After the Canvas declaration, use LaunchedEffect to request focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }




    // Functions to save and load store map
    fun saveStoreMap() {
        storeMap?.let { map ->
            try {
                val jsonString = json.encodeToString(map)
                File("store_map.json").writeText(jsonString)
            } catch (e: Exception) {
                println("Error saving store map: ${e.message}")
            }
        }
    }

    fun loadStoreMap() {
        try {
            val file = File("store_map.json")
            if (file.exists()) {
                val jsonString = file.readText()
                storeMap = json.decodeFromString<StoreMap>(jsonString)
                isDrawingFloor = false
                floorPoints = emptyList()
            }
        } catch (e: Exception) {
            println("Error loading store map: ${e.message}")
        }
    }

    if (selectedAisle != null) {
        AisleEditor(
            aisle = selectedAisle!!,
            onAisleUpdate = { updatedAisle ->
                // Update both the store map and selected aisle
                storeMap = storeMap?.copy(
                    floor = storeMap!!.floor.copy(
                        aisles = storeMap!!.floor.aisles.map { aisle ->
                            if (aisle.id == updatedAisle.id) updatedAisle else aisle
                        }
                    )
                )
                selectedAisle = updatedAisle  // Update the selected aisle reference
            },
            onBack = { selectedAisle = null }
        )
        return
    }

    val scope = rememberCoroutineScope()

    Row(modifier = modifier.fillMaxSize()) {
        // Side Panel
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Text(
                text = "Tools",
                modifier = Modifier.padding(8.dp)
            )
            Divider()
            Button(
                onClick = { isDrawingAisle = !isDrawingAisle },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(if (isDrawingAisle) "Cancel Aisle" else "Draw Aisle")
            }
            
            // Add Save/Load buttons
            Button(
                onClick = { saveStoreMap() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                enabled = storeMap != null
            ) {
                Text("Save Map")
            }
            
            Button(
                onClick = { loadStoreMap() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Load Map")
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        // Zoom out logic
                        scale = (scale - 0.25f).coerceAtLeast(minScale)
                    },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("-")
                }

                Button(
                    onClick = {
                        // Zoom in logic
                        scale = (scale + 0.25f).coerceAtMost(maxScale)
                    }
                ) {
                    Text("+")
                }
            }

            Text("Navigation Controls:", modifier = Modifier.padding(vertical = 4.dp))

            // Up button
            Button(
                onClick = { offset = offset.copy(y = offset.y + moveStep) },
                modifier = Modifier.width(120.dp)
            ) {
                Text("▲")
            }

            // Left, Reset Position, Right buttons
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { offset = offset.copy(x = offset.x + moveStep) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("◄")
                }

                Button(
                    onClick = { offset = Offset.Zero },
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("0")
                }

                Button(
                    onClick = { offset = offset.copy(x = offset.x - moveStep) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("►")
                }
            }

            // Down button
            Button(
                onClick = { offset = offset.copy(y = offset.y - moveStep) },
                modifier = Modifier.size(40.dp)
            ) {
                Text("▼")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))


        }

        Column(modifier = Modifier.weight(1f)) {
            // Toolbar
            TopAppBar(
                title = { Text("Store Map Editor") },
                actions = {
                    Button(
                        onClick = { 
                            if (isDrawingFloor && floorPoints.size >= 3) {
                                storeMap = StoreMap(
                                    floor = StoreFloor(
                                        id = UUID.randomUUID().toString(),
                                        vertices = floorPoints
                                    )
                                )
                                isDrawingFloor = false
                                floorPoints = emptyList()
                            } else {
                                isDrawingFloor = true
                                isDrawingAisle = false
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(if (isDrawingFloor) "Finish Floor" else "Draw Floor")
                    }
                }
            )

            // Main canvas
            Box(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (!isDrawingAisle) {
                                    scale *= zoom
                                    offset += pan
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { position ->
                                if (isDrawingFloor) {
                                    val point = Point(
                                        x = (position.x - offset.x) / scale,
                                        y = (position.y - offset.y) / scale
                                    )
                                    floorPoints = floorPoints + point
                                } else if (!isDrawingAisle && storeMap != null) {
                                    // Check if an aisle was clicked
                                    val clickPoint = Point(
                                        x = (position.x - offset.x) / scale,
                                        y = (position.y - offset.y) / scale
                                    )

                                    println("Click position $position")
                                    println("Click point $clickPoint")
                                    
                                    selectedAisle = storeMap!!.floor.aisles.find { aisle ->
                                        val isXInBounds = clickPoint.x >= aisle.position.x && 
                                            clickPoint.x <= aisle.position.x + aisle.width
                                        val isYInBounds = clickPoint.y >= aisle.position.y && 
                                            clickPoint.y <= aisle.position.y + aisle.length
                                        isXInBounds && isYInBounds
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    if (isDrawingAisle && storeMap != null) {
                                        startPoint = position
                                        currentPoint = position
                                        hasCollision = false
                                    }
                                },
                                onDrag = { change: PointerInputChange, _ ->
                                    if (isDrawingAisle) {
                                        currentPoint = change.position
                                        
                                        // Check for collisions during drag
                                        if (startPoint != null && currentPoint != null && storeMap != null) {
                                            val previewAisle = createPreviewAisle(
                                                startPoint!!,
                                                currentPoint!!,
                                                offset,
                                                scale
                                            )
                                            hasCollision = checkAisleCollision(
                                                previewAisle,
                                                storeMap!!.floor.aisles,
                                                storeMap!!
                                            )
                                        }
                                        
                                        change.consume()
                                    }
                                },
                                onDragEnd = {
                                    if (isDrawingAisle && startPoint != null && currentPoint != null && storeMap != null && !hasCollision) {
                                        val newAisle = createPreviewAisle(
                                            startPoint!!,
                                            currentPoint!!,
                                            offset,
                                            scale
                                        )

                                        storeMap = storeMap!!.copy(
                                            floor = storeMap!!.floor.copy(
                                                aisles = storeMap!!.floor.aisles + newAisle
                                            )
                                        )

                                        startPoint = null
                                        currentPoint = null
                                        isDrawingAisle = false
                                        hasCollision = false
                                    }
                                },
                                onDragCancel = {
                                    startPoint = null
                                    currentPoint = null
                                    hasCollision = false
                                }
                            )
                        }
                        .focusable()
                        .focusRequester(focusRequester)
                        .onKeyEvent { keyEvent ->
                            println("Key event: ${keyEvent.key}")
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    offset = offset.copy(x = offset.x + 20f)
                                    true
                                }
                                Key.DirectionRight -> {
                                    offset = offset.copy(x = offset.x - 20f)
                                    true
                                }
                                Key.DirectionUp -> {
                                    offset = offset.copy(y = offset.y + 20f)
                                    true
                                }
                                Key.DirectionDown -> {
                                    offset = offset.copy(y = offset.y - 20f)
                                    true
                                }
                                else -> false
                            }
                        }


                ) {

                    withTransform({
                        // First translate to the center or desired origin point
                        translate(offset.x, offset.y)
                        // Then scale around that point
                        scale(scale, pivot = Offset(0f, 0f))
                    }) {
                        // Draw your content here
                        if (floorPoints.isNotEmpty()) {
                            drawPoints(floorPoints)
                        }

                        // Draw store map elements if they exist
                        storeMap?.let { map ->
                            drawStoreMap(map)
                        }
                    }



                    // Draw aisle preview while dragging
                    if (isDrawingAisle && startPoint != null && currentPoint != null) {
                        drawAislePreview(
                            start = startPoint!!,
                            current = currentPoint!!,
                            existingAisles = storeMap?.floor?.aisles ?: emptyList(),
                            scale = scale
                        )
                    }
                }
            }

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { scale = 1f; offset = Offset.Zero },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Reset View")
                }
                
                Text("Scale: ${String.format("%.2f", scale)}x")
            }
        }
    }
}

private fun createPreviewAisle(start: Offset, current: Offset, offset: Offset, scale: Float): Aisle {
    val startPoint = Point(
        x = (start.x - offset.x) / scale,
        y = (start.y - offset.y) / scale
    )
    val endPoint = Point(
        x = (current.x - offset.x) / scale,
        y = (current.y - offset.y) / scale
    )

    val width = abs(endPoint.x - startPoint.x)
    val length = abs(endPoint.y - startPoint.y)
    
    val position = Point(
        x = minOf(startPoint.x, endPoint.x),
        y = minOf(startPoint.y, endPoint.y)
    )

    return Aisle(
        id = UUID.randomUUID().toString(),
        position = position,
        width = width,
        length = length,
        height = 200f
    )
}

private fun checkAisleCollision(aisle: Aisle, existingAisles: List<Aisle>, storeMap: StoreMap): Boolean {
    // Check if the new aisle overlaps with any existing aisles
    val hasAisleCollision = existingAisles.any { existing ->
        // Check if rectangles overlap
        val newLeft = aisle.position.x
        val newRight = aisle.position.x + aisle.width
        val newTop = aisle.position.y
        val newBottom = aisle.position.y + aisle.length

        val existingLeft = existing.position.x
        val existingRight = existing.position.x + existing.width
        val existingTop = existing.position.y
        val existingBottom = existing.position.y + existing.length

        // Rectangles overlap if they overlap on both x and y axes
        val xOverlap = newLeft < existingRight && newRight > existingLeft
        val yOverlap = newTop < existingBottom && newBottom > existingTop

        xOverlap && yOverlap
    }

    // Check if aisle is outside floor boundaries
    val isOutsideFloor = storeMap?.floor?.let { floor ->
        val vertices = floor.vertices
        if (vertices.size < 3) return@let true

        val aislePoints = listOf(
            Point(aisle.position.x, aisle.position.y),
            Point(aisle.position.x + aisle.width, aisle.position.y),
            Point(aisle.position.x + aisle.width, aisle.position.y + aisle.length),
            Point(aisle.position.x, aisle.position.y + aisle.length)
        )

        // If any point of the aisle is outside the floor polygon, return true
        aislePoints.any { point -> !isPointInPolygon(point, vertices) }
    } ?: true // If no floor exists, consider it as outside

    return hasAisleCollision || isOutsideFloor
}

private fun isPointInPolygon(point: Point, vertices: List<Point>): Boolean {
    var inside = false
    var j = vertices.size - 1
    
    for (i in vertices.indices) {
        if ((vertices[i].y > point.y) != (vertices[j].y > point.y) &&
            point.x < (vertices[j].x - vertices[i].x) * (point.y - vertices[i].y) / 
            (vertices[j].y - vertices[i].y) + vertices[i].x
        ) {
            inside = !inside
        }
        j = i
    }
    
    return inside
}
