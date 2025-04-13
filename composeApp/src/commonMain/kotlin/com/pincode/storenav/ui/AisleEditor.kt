package com.pincode.storenav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pincode.storenav.model.Aisle
import com.pincode.storenav.model.Point
import com.pincode.storenav.model.Shelf
import java.util.*

@Composable
fun AisleEditor(
    aisle: Aisle,
    onAisleUpdate: (Aisle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var shelfWeight by remember { mutableStateOf("1.0") }
    var selectedShelf by remember { mutableStateOf<Pair<Shelf, Boolean>?>(null) } // Shelf and side (true = side one, false = side two)
    var numRowsToAdd by remember { mutableStateOf("1") }

    // Center the aisle in the view initially
    LaunchedEffect(Unit) {
        offset = Offset(100f, 100f)
    }

    // Determine orientation
    val isHorizontal = aisle.length > aisle.width

    if (selectedShelf != null) {
        ShelfEditor(
            shelf = selectedShelf!!.first,
            onShelfUpdate = { updatedShelf ->
                // Update the appropriate side
                if (selectedShelf!!.second) { // side one
                    val updatedSideOne = aisle.sideOneShelves.map { 
                        if (it.id == updatedShelf.id) updatedShelf else it 
                    }
                    onAisleUpdate(aisle.copy(sideOneShelves = updatedSideOne))
                } else { // side two
                    val updatedSideTwo = aisle.sideTwoShelves.map { 
                        if (it.id == updatedShelf.id) updatedShelf else it 
                    }
                    onAisleUpdate(aisle.copy(sideTwoShelves = updatedSideTwo))
                }
                selectedShelf = selectedShelf!!.copy(first = updatedShelf)
            },
            numRowsToAdd = numRowsToAdd,
            onNumRowsToAddChange = { numRowsToAdd = it },
            onBack = { selectedShelf = null },
            isParentAisleHorizontal = isHorizontal
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Aisle Editor") },
            navigationIcon = {
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
        )

        // Add shelf weight input
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = shelfWeight,
                onValueChange = { shelfWeight = it },
                label = { Text("Shelf Weight") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val weight = shelfWeight.toFloatOrNull() ?: return@Button
                if (weight <= 0) return@Button
                
                val newShelf = Shelf(
                    id = UUID.randomUUID().toString(),
                    weight = weight
                )
                
                // Update both sides of the aisle
                onAisleUpdate(aisle.copy(
                    sideOneShelves = aisle.sideOneShelves + newShelf,
                    sideTwoShelves = aisle.sideTwoShelves + newShelf
                ))
            }) {
                Text("Add Shelf")
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
                    .pointerInput(Unit) {
                        detectTapGestures { position ->
                            // Check if a shelf was tapped
                            val adjustedPosition = Point(
                                x = (position.x - offset.x) / scale,
                                y = (position.y - offset.y) / scale
                            )
                            
                            // Calculate shelf areas based on orientation
                            val spacing = 4f
                            
                            if (isHorizontal) {
                                // Horizontal layout (length > width)
                                val shelfAreaWidth = (aisle.width - spacing) / 2
                                
                                // Check side one shelves (left side)
                                var currentY = spacing
                                for (shelf in aisle.sideOneShelves) {
                                    val shelfHeight = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideOneShelves)
                                    val shelfArea = Rect(
                                        offset = Offset(aisle.position.x + spacing, aisle.position.y + currentY),
                                        size = Size(shelfAreaWidth - spacing, shelfHeight)
                                    )
                                    
                                    if (isPointInRect(adjustedPosition, shelfArea)) {
                                        selectedShelf = Pair(shelf, true)
                                        return@detectTapGestures
                                    }
                                    currentY += shelfHeight + spacing
                                }
                                
                                // Check side two shelves (right side)
                                currentY = spacing
                                for (shelf in aisle.sideTwoShelves) {
                                    val shelfHeight = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideTwoShelves)
                                    val shelfArea = Rect(
                                        offset = Offset(aisle.position.x + shelfAreaWidth + spacing, aisle.position.y + currentY),
                                        size = Size(shelfAreaWidth - spacing, shelfHeight)
                                    )
                                    
                                    if (isPointInRect(adjustedPosition, shelfArea)) {
                                        selectedShelf = Pair(shelf, false)
                                        return@detectTapGestures
                                    }
                                    currentY += shelfHeight + spacing
                                }
                            } else {
                                // Vertical layout (width > length)
                                val shelfAreaHeight = (aisle.length - spacing) / 2
                                
                                // Check side one shelves (top side)
                                var currentX = spacing
                                for (shelf in aisle.sideOneShelves) {
                                    val shelfWidth = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideOneShelves)
                                    val shelfArea = Rect(
                                        offset = Offset(aisle.position.x + currentX, aisle.position.y + spacing),
                                        size = Size(shelfWidth, shelfAreaHeight - spacing)
                                    )
                                    
                                    if (isPointInRect(adjustedPosition, shelfArea)) {
                                        selectedShelf = Pair(shelf, true)
                                        return@detectTapGestures
                                    }
                                    currentX += shelfWidth + spacing
                                }
                                
                                // Check side two shelves (bottom side)
                                currentX = spacing
                                for (shelf in aisle.sideTwoShelves) {
                                    val shelfWidth = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideTwoShelves)
                                    val shelfArea = Rect(
                                        offset = Offset(aisle.position.x + currentX, aisle.position.y + shelfAreaHeight + spacing),
                                        size = Size(shelfWidth, shelfAreaHeight - spacing)
                                    )
                                    
                                    if (isPointInRect(adjustedPosition, shelfArea)) {
                                        selectedShelf = Pair(shelf, false)
                                        return@detectTapGestures
                                    }
                                    currentX += shelfWidth + spacing
                                }
                            }
                        }
                    }
            ) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, pivot = Offset(0f, 0f))
                }) {
                    // Draw aisle outline
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(aisle.position.x, aisle.position.y),
                        size = Size(aisle.width, aisle.length),
                        style = Fill
                    )

                    val spacing = 4f
                    
                    if (isHorizontal) {
                        // Horizontal layout (length > width)
                        val shelfAreaWidth = (aisle.width - spacing) / 2
                        
                        // Draw side one shelves (left side)
                        var currentY = spacing
                        aisle.sideOneShelves.forEach { shelf ->
                            val shelfHeight = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideOneShelves)
                            val shelfLeft = aisle.position.x + spacing
                            val shelfWidth = shelfAreaWidth - spacing
                            
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(shelfLeft, aisle.position.y + currentY),
                                size = Size(shelfWidth, shelfHeight),
                                style = Stroke(width = 2f)
                            )
                            
                            // Draw rows within the shelf - rows should be vertical for vertical shelves
                            if (shelf.rows.isNotEmpty()) {
                                val rowSpacing = 2f
                                val rowAreaWidth = shelfWidth - 4f // Small margin on left and right
                                val rowWidth = (rowAreaWidth - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                                
                                shelf.rows.forEachIndexed { index, _ ->
                                    val rowX = shelfLeft + 2f + (index * (rowWidth + rowSpacing))
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = Offset(rowX, aisle.position.y + currentY + 2f),
                                        size = Size(rowWidth, shelfHeight - 4f),
                                        style = Stroke(width = 1f)
                                    )
                                }
                            }
                            
                            currentY += shelfHeight + spacing
                        }
                        
                        // Draw side two shelves (right side)
                        currentY = spacing
                        aisle.sideTwoShelves.forEach { shelf ->
                            val shelfHeight = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideTwoShelves)
                            val shelfLeft = aisle.position.x + shelfAreaWidth + spacing
                            val shelfWidth = shelfAreaWidth - spacing
                            
                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(shelfLeft, aisle.position.y + currentY),
                                size = Size(shelfWidth, shelfHeight),
                                style = Stroke(width = 2f)
                            )
                            
                            // Draw rows within the shelf - rows should be vertical for vertical shelves
                            if (shelf.rows.isNotEmpty()) {
                                val rowSpacing = 2f
                                val rowAreaWidth = shelfWidth - 4f // Small margin on left and right
                                val rowWidth = (rowAreaWidth - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                                
                                shelf.rows.forEachIndexed { index, _ ->
                                    val rowX = shelfLeft + 2f + (index * (rowWidth + rowSpacing))
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = Offset(rowX, aisle.position.y + currentY + 2f),
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
                        var currentX = spacing
                        aisle.sideOneShelves.forEach { shelf ->
                            val shelfWidth = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideOneShelves)
                            val shelfTop = aisle.position.y + spacing
                            val shelfHeight = shelfAreaHeight - spacing
                            
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(aisle.position.x + currentX, shelfTop),
                                size = Size(shelfWidth, shelfHeight),
                                style = Stroke(width = 2f)
                            )
                            
                            // Draw rows within the shelf - rows should be horizontal for horizontal shelves
                            if (shelf.rows.isNotEmpty()) {
                                val rowSpacing = 2f
                                val rowAreaHeight = shelfHeight - 4f // Small margin on top and bottom
                                val rowHeight = (rowAreaHeight - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                                
                                shelf.rows.forEachIndexed { index, _ ->
                                    val rowY = shelfTop + 2f + (index * (rowHeight + rowSpacing))
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = Offset(aisle.position.x + currentX + 2f, rowY),
                                        size = Size(shelfWidth - 4f, rowHeight),
                                        style = Stroke(width = 1f)
                                    )
                                }
                            }
                            
                            currentX += shelfWidth + spacing
                        }
                        
                        // Draw side two shelves (bottom side)
                        currentX = spacing
                        aisle.sideTwoShelves.forEach { shelf ->
                            val shelfWidth = calculateShelfSize(shelf.weight, aisle, isHorizontal, aisle.sideTwoShelves)
                            val shelfTop = aisle.position.y + shelfAreaHeight + spacing
                            val shelfHeight = shelfAreaHeight - spacing
                            
                            drawRect(
                                color = Color.Green,
                                topLeft = Offset(aisle.position.x + currentX, shelfTop),
                                size = Size(shelfWidth, shelfHeight),
                                style = Stroke(width = 2f)
                            )
                            
                            // Draw rows within the shelf - rows should be horizontal for horizontal shelves
                            if (shelf.rows.isNotEmpty()) {
                                val rowSpacing = 2f
                                val rowAreaHeight = shelfHeight - 4f // Small margin on top and bottom
                                val rowHeight = (rowAreaHeight - (rowSpacing * (shelf.rows.size - 1))) / shelf.rows.size
                                
                                shelf.rows.forEachIndexed { index, _ ->
                                    val rowY = shelfTop + 2f + (index * (rowHeight + rowSpacing))
                                    drawRect(
                                        color = Color.Blue,
                                        topLeft = Offset(aisle.position.x + currentX + 2f, rowY),
                                        size = Size(shelfWidth - 4f, rowHeight),
                                        style = Stroke(width = 1f)
                                    )
                                }
                            }
                            
                            currentX += shelfWidth + spacing
                        }
                    }
                }
            }
        }
    }
}

// Helper function to check if a point is inside a rectangle
private fun isPointInRect(point: Point, rect: Rect): Boolean {
    return point.x >= rect.left && point.x <= rect.right &&
           point.y >= rect.top && point.y <= rect.bottom
}

// Calculate the size of a shelf based on weight and available space
private fun calculateShelfSize(weight: Float, aisle: Aisle, isHorizontal: Boolean, shelves: List<Shelf>): Float {
    val spacing = 4f
    val totalWeight = shelves.sumOf { it.weight.toDouble() }.toFloat()
    
    // Available space depends on orientation
    val availableSpace = if (isHorizontal) {
        // Horizontal layout (length > width)
        aisle.length - (spacing * (shelves.size + 1))
    } else {
        // Vertical layout (width > length)
        aisle.width - (spacing * (shelves.size + 1))
    }
    
    // Calculate proportion based on weight
    return if (totalWeight > 0) {
        (weight / totalWeight) * availableSpace
    } else {
        0f
    }
} 