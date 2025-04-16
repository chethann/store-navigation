package com.pincode.storenav.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pincode.storenav.model.Point
import com.pincode.storenav.model.StoreMap
import com.pincode.storenav.util.PathFinder

@Composable
fun StoreNavigationControls(
    storeMap: StoreMap?,
    onPathCalculated: (List<PathFinder.WaypointData>?) -> Unit
) {
    if (storeMap == null) {
        return
    }
    var shelfIds by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pathFinder = remember(storeMap) { PathFinder(storeMap) }
    
    Column(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Navigate to Shelves",
            style = MaterialTheme.typography.h6
        )
        

            OutlinedTextField(
                value = shelfIds,
                onValueChange = { shelfIds = it; errorMessage = null },
                label = { Text("Enter Shelf IDs (comma-separated)") },
                modifier = Modifier.fillMaxWidth(1f),
                singleLine = true,
                placeholder = { Text("e.g. shelf1,shelf2,shelf3") }
            )
            
            Button(
                onClick = {
                    val idList = shelfIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (idList.isEmpty()) {
                        errorMessage = "Please enter at least one shelf ID"
                        onPathCalculated(null)
                        return@Button
                    }
                    
                    val path = pathFinder.findPathToMultipleShelves(idList)
                    if (path == null) {
                        errorMessage = "Could not find a path to all shelves"
                    } else {
                        errorMessage = null
                    }
                    onPathCalculated(path)
                },
                enabled = shelfIds.isNotBlank()
            ) {
                Icon(Icons.Default.Search, contentDescription = "Find Path")
                Spacer(Modifier.width(4.dp))
                Text("Find Path")
            }
        
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption
            )
        }
    }
}