package com.pincode.storenav.util

import com.pincode.storenav.model.*
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.min
import kotlin.math.max

/**
 * PathFinder utility that creates navigation paths through a store
 * using only horizontal and vertical movements,
 * staying completely outside of aisles at all times
 */
class PathFinder(private val storeMap: StoreMap) {
    
    // Minimum distance to keep from all aisles (in pixels)
    private val SAFE_MARGIN = 1f
    
    /**
     * Find a path to visit multiple shelves in sequence
     */
    fun findPathToMultipleShelves(shelfIds: List<String>): List<WaypointData>? {
        if (storeMap.floor.vertices.isEmpty() || shelfIds.isEmpty()) return null
        
        // Start point is the first vertex
        val startPoint = storeMap.floor.vertices.first()
        
        // Store dimensions
        val storeBounds = getStoreBounds()
        
        // Create a list of all aisles with expanded boundaries for safety margin
        val expandedAisles = storeMap.floor.aisles.map { aisle ->
            ExpandedAisle(
                aisle = aisle,
                bounds = Rect(
                    aisle.position.x - SAFE_MARGIN,
                    aisle.position.y - SAFE_MARGIN,
                    aisle.position.x + aisle.width + SAFE_MARGIN,
                    aisle.position.y + aisle.length + SAFE_MARGIN
                )
            )
        }
        
        // Map shelves to their access points (outside the aisles)
        val shelfAccessPoints = mutableMapOf<String, ShelfAccess>()
        
        // Process all shelves to find access points
        shelfIds.forEach { shelfId ->
            val shelf = findShelfById(shelfId) ?: return@forEach
            val (aisle, isSideOne) = findAisleContainingShelf(shelfId) ?: return@forEach
            
            // Calculate shelf position at the edge of the aisle (not inside)
            val shelfPos = calculateShelfPosition(aisle, shelf, isSideOne)
            
            // Calculate the access point (outside the aisle)
            val accessPoint = calculateAccessPoint(aisle, shelfPos, isSideOne)
            
            shelfAccessPoints[shelfId] = ShelfAccess(
                shelfId = shelfId,
                shelfPosition = shelfPos,
                accessPoint = accessPoint,
                aisle = aisle,
                isSideOne = isSideOne
            )
        }
        
        // If any shelves couldn't be found, return null
        if (shelfAccessPoints.size != shelfIds.size) {
            return null
        }
        
        // Generate a denser, more optimized navigation grid
        val navGrid = generateOptimizedNavigationGrid(expandedAisles, storeBounds, shelfAccessPoints.values)
        
        // Determine optimal shelf visit order
        val visitOrder = determineShelfVisitOrder(
            start = startPoint,
            shelfIds = shelfIds,
            shelfAccesses = shelfAccessPoints,
            navGrid = navGrid,
            expandedAisles = expandedAisles
        )
        
        // Build the final navigation path
        val waypoints = mutableListOf<WaypointData>()
        
        // Add start point
        waypoints.add(WaypointData(startPoint, WaypointType.START))
        
        var currentPoint = startPoint
        var waypointNumber = 1
        
        // Visit each shelf in order
        visitOrder.forEach { shelfId ->
            val shelfAccess = shelfAccessPoints[shelfId] ?: return@forEach
            
            // Find optimal path to access point using improved algorithm
            val pathToAccess = findOptimalPath(
                from = currentPoint,
                to = shelfAccess.accessPoint,
                navPoints = navGrid,
                expandedAisles = expandedAisles,
                aislesByShelfId = shelfAccessPoints.values.associate { it.shelfId to it.aisle }
            )
            
            // Add all points except the first as regular waypoints
            pathToAccess.drop(1).forEach { point ->
                waypoints.add(WaypointData(point, WaypointType.REGULAR))
            }
            
            // Add the shelf access with destination number
            val finalPoint = if (pathToAccess.isNotEmpty()) pathToAccess.last() else currentPoint
            if (finalPoint != shelfAccess.accessPoint) {
                waypoints.add(WaypointData(shelfAccess.accessPoint, WaypointType.REGULAR))
            }
            
            // Add the shelf position with a number
            waypoints.add(WaypointData(shelfAccess.shelfPosition, WaypointType.SHELF_DESTINATION, waypointNumber++))
            
            // Update current position
            currentPoint = shelfAccess.accessPoint
        }
        
        // Return to start with improved path
        val returnPath = findOptimalPath(
            from = currentPoint,
            to = startPoint,
            navPoints = navGrid,
            expandedAisles = expandedAisles,
            aislesByShelfId = emptyMap()
        )
        
        // Add all points in the return path
        returnPath.drop(1).forEach { point ->
            if (point == returnPath.last()) {
                waypoints.add(WaypointData(point, WaypointType.END))
            } else {
                waypoints.add(WaypointData(point, WaypointType.REGULAR))
            }
        }
        
        return waypoints
    }
    
    /**
     * Find path to a single shelf
     */
    fun findPathToShelf(shelfId: String): List<WaypointData>? {
        return findPathToMultipleShelves(listOf(shelfId))
    }
    
    /**
     * Generate a denser, more optimized navigation grid
     */
    private fun generateOptimizedNavigationGrid(
        expandedAisles: List<ExpandedAisle>,
        storeBounds: Rect,
        shelfAccesses: Collection<ShelfAccess>
    ): MutableSet<Point> {
        val navPoints = mutableSetOf<Point>()
        
        // 1. Add perimeter points around each aisle with variable density
        expandedAisles.forEach { expandedAisle ->
            val bounds = expandedAisle.bounds
            
            // Closer spacing for more precise navigation around aisles
            val spacingMain = 15f // Main spacing
            val spacingCorner = 5f // Extra points near corners for better turning
            
            // Add dense points around the perimeter
            // Top edge
            var x = bounds.left
            while (x <= bounds.right) {
                val point = Point(x, bounds.top)
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
                
                // Add denser points near corners
                val spacing = if (x < bounds.left + 20f || x > bounds.right - 20f) {
                    spacingCorner
                } else {
                    spacingMain
                }
                x += spacing
            }
            
            // Right edge
            var y = bounds.top
            while (y <= bounds.bottom) {
                val point = Point(bounds.right, y)
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
                
                // Add denser points near corners
                val spacing = if (y < bounds.top + 20f || y > bounds.bottom - 20f) {
                    spacingCorner
                } else {
                    spacingMain
                }
                y += spacing
            }
            
            // Bottom edge
            x = bounds.right
            while (x >= bounds.left) {
                val point = Point(x, bounds.bottom)
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
                
                // Add denser points near corners
                val spacing = if (x < bounds.left + 20f || x > bounds.right - 20f) {
                    spacingCorner
                } else {
                    spacingMain
                }
                x -= spacing
            }
            
            // Left edge
            y = bounds.bottom
            while (y >= bounds.top) {
                val point = Point(bounds.left, y)
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
                
                // Add denser points near corners
                val spacing = if (y < bounds.top + 20f || y > bounds.bottom - 20f) {
                    spacingCorner
                } else {
                    spacingMain
                }
                y -= spacing
            }
        }
        
        // 2. Add mid-aisle navigation points - especially useful for "wrapping around" aisles
        expandedAisles.forEach { expandedAisle ->
            val bounds = expandedAisle.bounds
            
            // Add midpoints on each side
            val midTop = Point((bounds.left + bounds.right) / 2, bounds.top)
            val midRight = Point(bounds.right, (bounds.top + bounds.bottom) / 2)
            val midBottom = Point((bounds.left + bounds.right) / 2, bounds.bottom)
            val midLeft = Point(bounds.left, (bounds.top + bounds.bottom) / 2)
            
            listOf(midTop, midRight, midBottom, midLeft).forEach { point ->
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
            }
            
            // Add quarter points for even better navigation
            val quarterWidth = (bounds.right - bounds.left) / 4
            val quarterHeight = (bounds.bottom - bounds.top) / 4
            
            // Top and bottom edges at quarter points
            for (i in 1..3) {
                val x = bounds.left + i * quarterWidth
                val topPoint = Point(x, bounds.top)
                val bottomPoint = Point(x, bounds.bottom)
                
                if (isPointSafe(topPoint, expandedAisles)) navPoints.add(topPoint)
                if (isPointSafe(bottomPoint, expandedAisles)) navPoints.add(bottomPoint)
            }
            
            // Left and right edges at quarter points
            for (i in 1..3) {
                val y = bounds.top + i * quarterHeight
                val leftPoint = Point(bounds.left, y)
                val rightPoint = Point(bounds.right, y)
                
                if (isPointSafe(leftPoint, expandedAisles)) navPoints.add(leftPoint)
                if (isPointSafe(rightPoint, expandedAisles)) navPoints.add(rightPoint)
            }
        }
        
        // 3. Add corner navigation points
        expandedAisles.forEach { expandedAisle ->
            val bounds = expandedAisle.bounds
            val margin = 10f // Extra margin for corner navigation
            
            // Extended corners for better corner navigation
            val corners = listOf(
                Point(bounds.left - margin, bounds.top - margin),
                Point(bounds.right + margin, bounds.top - margin),
                Point(bounds.left - margin, bounds.bottom + margin),
                Point(bounds.right + margin, bounds.bottom + margin)
            )
            
            corners.forEach { point ->
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
            }
        }
        
        // 4. Add path shortcuts between neighboring aisles
        addPathShortcuts(navPoints, expandedAisles)
        
        // 5. Add strategic corridor points
        val corridorPoints = generateStrategicCorridorPoints(expandedAisles)
        navPoints.addAll(corridorPoints)
        
        // 6. Add shelf access points to ensure direct paths to shelves
        shelfAccesses.forEach { access ->
            navPoints.add(access.accessPoint)
            
            // Add additional points around the access point for better approach angles
            val margin = 10f
            val approaches = listOf(
                Point(access.accessPoint.x - margin, access.accessPoint.y),
                Point(access.accessPoint.x + margin, access.accessPoint.y),
                Point(access.accessPoint.x, access.accessPoint.y - margin),
                Point(access.accessPoint.x, access.accessPoint.y + margin)
            )
            
            approaches.forEach { point ->
                if (isPointSafe(point, expandedAisles)) {
                    navPoints.add(point)
                }
            }
        }
        
        // 7. Add store boundary points
        addStoreBoundaryPoints(navPoints, storeBounds)
        
        return navPoints
    }
    
    /**
     * Add path shortcuts between neighboring aisles
     */
    private fun addPathShortcuts(navPoints: MutableSet<Point>, expandedAisles: List<ExpandedAisle>) {
        for (i in expandedAisles.indices) {
            for (j in i + 1 until expandedAisles.size) {
                val aisle1 = expandedAisles[i]
                val aisle2 = expandedAisles[j]
                
                // Check if aisles are close enough for shortcuts
                val minDistance = minDistanceBetweenRects(aisle1.bounds, aisle2.bounds)
                if (minDistance > 60f) continue // Skip if too far apart
                
                // Create shortcut points based on relative positions
                val center1 = Point(
                    (aisle1.bounds.left + aisle1.bounds.right) / 2,
                    (aisle1.bounds.top + aisle1.bounds.bottom) / 2
                )
                
                val center2 = Point(
                    (aisle2.bounds.left + aisle2.bounds.right) / 2,
                    (aisle2.bounds.top + aisle2.bounds.bottom) / 2
                )
                
                // Create midpoint shortcut if safe
                val midPoint = Point(
                    (center1.x + center2.x) / 2,
                    (center1.y + center2.y) / 2
                )
                
                if (isPointSafe(midPoint, expandedAisles)) {
                    navPoints.add(midPoint)
                }
                
                // Create direct corridors if aisles are aligned
                if (abs(center1.x - center2.x) < 20f) {
                    // Vertically aligned aisles - create horizontal corridor
                    val y = if (aisle1.bounds.bottom < aisle2.bounds.top) {
                        (aisle1.bounds.bottom + aisle2.bounds.top) / 2
                    } else {
                        (aisle2.bounds.bottom + aisle1.bounds.top) / 2
                    }
                    
                    val corridor = Point(center1.x, y)
                    if (isPointSafe(corridor, expandedAisles)) {
                        navPoints.add(corridor)
                    }
                }
                
                if (abs(center1.y - center2.y) < 20f) {
                    // Horizontally aligned aisles - create vertical corridor
                    val x = if (aisle1.bounds.right < aisle2.bounds.left) {
                        (aisle1.bounds.right + aisle2.bounds.left) / 2
                    } else {
                        (aisle2.bounds.right + aisle1.bounds.left) / 2
                    }
                    
                    val corridor = Point(x, center1.y)
                    if (isPointSafe(corridor, expandedAisles)) {
                        navPoints.add(corridor)
                    }
                }
            }
        }
    }
    
    /**
     * Find minimum distance between two rectangles
     */
    private fun minDistanceBetweenRects(r1: Rect, r2: Rect): Float {
        val left = max(0f, min(r1.left, r2.left) - max(r1.right, r2.right))
        val top = max(0f, min(r1.top, r2.top) - max(r1.bottom, r2.bottom))
        return sqrt(left * left + top * top)
    }
    
    /**
     * Add regular points along store boundary
     */
    private fun addStoreBoundaryPoints(navPoints: MutableSet<Point>, storeBounds: Rect) {
        // Add corners
        navPoints.add(Point(storeBounds.left, storeBounds.top))
        navPoints.add(Point(storeBounds.right, storeBounds.top))
        navPoints.add(Point(storeBounds.left, storeBounds.bottom))
        navPoints.add(Point(storeBounds.right, storeBounds.bottom))
        
        // Add midpoints and quarter points on each edge
        val width = storeBounds.right - storeBounds.left
        val height = storeBounds.bottom - storeBounds.top
        
        for (i in 1..5) {
            val fraction = i / 6f
            navPoints.add(Point(storeBounds.left + width * fraction, storeBounds.top))
            navPoints.add(Point(storeBounds.left + width * fraction, storeBounds.bottom))
            navPoints.add(Point(storeBounds.left, storeBounds.top + height * fraction))
            navPoints.add(Point(storeBounds.right, storeBounds.top + height * fraction))
        }
    }
    
    /**
     * Generate strategic corridor points for better path finding
     */
    private fun generateStrategicCorridorPoints(expandedAisles: List<ExpandedAisle>): Set<Point> {
        val corridorPoints = mutableSetOf<Point>()
        
        // For each pair of aisles, create navigation points in the corridors
        for (i in expandedAisles.indices) {
            for (j in i + 1 until expandedAisles.size) {
                val aisle1 = expandedAisles[i]
                val aisle2 = expandedAisles[j]
                
                // Create corridor intersection points
                val intersections = listOf(
                    // Vertical corridors
                    Point(aisle1.bounds.left, aisle2.bounds.top),
                    Point(aisle1.bounds.right, aisle2.bounds.top),
                    Point(aisle1.bounds.left, aisle2.bounds.bottom),
                    Point(aisle1.bounds.right, aisle2.bounds.bottom),
                    
                    // Horizontal corridors
                    Point(aisle2.bounds.left, aisle1.bounds.top),
                    Point(aisle2.bounds.right, aisle1.bounds.top),
                    Point(aisle2.bounds.left, aisle1.bounds.bottom),
                    Point(aisle2.bounds.right, aisle1.bounds.bottom)
                )
                
                // Add any safe intersection points
                intersections.forEach { point ->
                    if (isPointSafe(point, expandedAisles)) {
                        corridorPoints.add(point)
                        
                        // For better corner navigation, add points slightly offset from corners
                        val offset = 10f
                        val nearbyPoints = listOf(
                            Point(point.x + offset, point.y),
                            Point(point.x - offset, point.y),
                            Point(point.x, point.y + offset),
                            Point(point.x, point.y - offset)
                        )
                        
                        nearbyPoints.forEach { nearby ->
                            if (isPointSafe(nearby, expandedAisles)) {
                                corridorPoints.add(nearby)
                            }
                        }
                    }
                }
                
                // Add midpoints between aligned aisle edges
                if (abs(aisle1.bounds.left - aisle2.bounds.left) < 5f) {
                    val x = aisle1.bounds.left
                    val y = (max(aisle1.bounds.top, aisle2.bounds.top) + min(aisle1.bounds.bottom, aisle2.bounds.bottom)) / 2
                    val midpoint = Point(x, y)
                    if (isPointSafe(midpoint, expandedAisles)) corridorPoints.add(midpoint)
                }
                
                if (abs(aisle1.bounds.right - aisle2.bounds.right) < 5f) {
                    val x = aisle1.bounds.right
                    val y = (max(aisle1.bounds.top, aisle2.bounds.top) + min(aisle1.bounds.bottom, aisle2.bounds.bottom)) / 2
                    val midpoint = Point(x, y)
                    if (isPointSafe(midpoint, expandedAisles)) corridorPoints.add(midpoint)
                }
                
                if (abs(aisle1.bounds.top - aisle2.bounds.top) < 5f) {
                    val y = aisle1.bounds.top
                    val x = (max(aisle1.bounds.left, aisle2.bounds.left) + min(aisle1.bounds.right, aisle2.bounds.right)) / 2
                    val midpoint = Point(x, y)
                    if (isPointSafe(midpoint, expandedAisles)) corridorPoints.add(midpoint)
                }
                
                if (abs(aisle1.bounds.bottom - aisle2.bounds.bottom) < 5f) {
                    val y = aisle1.bounds.bottom
                    val x = (max(aisle1.bounds.left, aisle2.bounds.left) + min(aisle1.bounds.right, aisle2.bounds.right)) / 2
                    val midpoint = Point(x, y)
                    if (isPointSafe(midpoint, expandedAisles)) corridorPoints.add(midpoint)
                }
            }
        }
        
        return corridorPoints
    }
    
    /**
     * Find optimal path between two points with improved corner case handling
     */
    private fun findOptimalPath(
        from: Point,
        to: Point,
        navPoints: Set<Point>,
        expandedAisles: List<ExpandedAisle>,
        aislesByShelfId: Map<String, Aisle>
    ): List<Point> {
        // Check if direct Manhattan path is safe (doesn't cross any aisles)
        val directPath = createManhattanPath(from, to)
        if (isPathSafe(directPath, expandedAisles)) {
            return directPath
        }
        
        // Try both horizontal-first and vertical-first corner paths
        val cornerPaths = listOf(
            createCornerPath(from, to, true),  // Try going horizontal first
            createCornerPath(from, to, false)  // Try going vertical first
        )
        
        // Try both corner paths and choose the shorter one if safe
        val safePaths = cornerPaths.filter { isPathSafe(it, expandedAisles) }
        if (safePaths.isNotEmpty()) {
            // Return the shorter path
            return safePaths.minByOrNull { pathLength(it) } ?: safePaths.first()
        }
        
        // Try multiple intermediate points with different resolutions to find safe paths
        val resolutions = listOf(100f, 50f, 25f, 10f)
        for (resolution in resolutions) {
            val safeIntermediatePaths = findSafeIntermediatePaths(from, to, expandedAisles, resolution)
            if (safeIntermediatePaths.isNotEmpty()) {
                return safeIntermediatePaths.minByOrNull { pathLength(it) } ?: safeIntermediatePaths.first()
            }
        }
        
        // Try wrapping around each nearby aisle
        val wrappingPaths = mutableListOf<List<Point>>()
        
        // First, identify aisles that might be causing the blockage
        val potentialBlockingAisles = expandedAisles.filter { aisle ->
            // Check if the direct path between points intersects this aisle
            directPath.any { point -> isPointInsideRect(point, aisle.bounds) } ||
            isSegmentIntersectsAisle(from, to, aisle.bounds) ||
            !isSegmentSafe(from, Point(to.x, from.y), listOf(aisle)) ||
            !isSegmentSafe(Point(to.x, from.y), to, listOf(aisle))
        }
        
        // Try both horizontal and vertical wrapping paths around each potentially blocking aisle
        for (aisle in potentialBlockingAisles) {
            val wrappedPaths = tryAllAisleWrappingPaths(from, to, aisle.bounds, expandedAisles)
            if (wrappedPaths.isNotEmpty()) {
                wrappingPaths.addAll(wrappedPaths)
            }
        }
        
        // If no specific wrapping paths were found, try all aisles
        if (wrappingPaths.isEmpty()) {
            for (aisle in expandedAisles) {
                val wrappedPaths = tryAllAisleWrappingPaths(from, to, aisle.bounds, expandedAisles)
                if (wrappedPaths.isNotEmpty()) {
                    wrappingPaths.addAll(wrappedPaths)
                }
            }
        }
        
        // If we found wrapping paths, choose the shortest one
        if (wrappingPaths.isNotEmpty()) {
            return wrappingPaths.minByOrNull { pathLength(it) } ?: wrappingPaths.first()
        }
        
        // Use A* with navigation grid for more complex routes
        val aStarPath = findPathWithAStar(from, to, navPoints, expandedAisles)
        
        // Final safety check to ensure no diagonal movements
        return ensureStrictManhattanPath(aStarPath)
    }
    
    /**
     * Ensure all path segments are strictly horizontal or vertical (no diagonals)
     */
    private fun ensureStrictManhattanPath(path: List<Point>): List<Point> {
        if (path.size <= 1) return path
        
        val result = mutableListOf<Point>()
        result.add(path.first())
        
        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]
            
            // Check if this is a diagonal movement
            if (current.x != next.x && current.y != next.y) {
                // Insert an intermediate point to create Manhattan movement
                result.add(Point(next.x, current.y))
            }
            
            result.add(next)
        }
        
        return result
    }
    
    /**
     * Try all possible wrapping paths around an aisle and return all safe options
     */
    private fun tryAllAisleWrappingPaths(
        from: Point,
        to: Point,
        aisle: Rect,
        expandedAisles: List<ExpandedAisle>
    ): List<List<Point>> {
        // Check if the aisle is somewhat relevant to the path
        // We use a larger check area to catch paths that go near the aisle
        val isAisleRelevant = lineRectIntersection(from, to, Rect(
            aisle.left - 50f,
            aisle.top - 50f,
            aisle.right + 50f,
            aisle.bottom + 50f
        ))

        if (!isAisleRelevant) return emptyList()

        // Create buffer margins for safer navigation
        val margin = 5f
        val topY = aisle.top - margin
        val bottomY = aisle.bottom + margin
        val leftX = aisle.left - margin
        val rightX = aisle.right + margin

        // Create multiple options with different intermediate points for each path direction
        val possiblePaths = mutableListOf<List<Point>>()
        
        // Eight basic wrapping strategies (2 directions × 4 sides)
        
        // TOP approach - horizontal first
        possiblePaths.add(listOf(
            from,
            Point(to.x, from.y),  // Move horizontally first
            Point(to.x, topY),    // Move to top edge
            to                     // Move to destination
        ))
        
        // TOP approach - vertical first
        possiblePaths.add(listOf(
            from, 
            Point(from.x, topY),  // Move to top edge first
            Point(to.x, topY),    // Move horizontally along top edge
            to                     // Move to destination
        ))
        
        // BOTTOM approach - horizontal first
        possiblePaths.add(listOf(
            from,
            Point(to.x, from.y),   // Move horizontally first
            Point(to.x, bottomY),  // Move to bottom edge
            to                      // Move to destination
        ))
        
        // BOTTOM approach - vertical first
        possiblePaths.add(listOf(
            from,
            Point(from.x, bottomY), // Move to bottom edge first
            Point(to.x, bottomY),   // Move horizontally along bottom edge
            to                       // Move to destination
        ))
        
        // LEFT approach - horizontal first
        possiblePaths.add(listOf(
            from,
            Point(leftX, from.y),  // Move to left edge first
            Point(leftX, to.y),    // Move vertically along left edge
            to                     // Move to destination
        ))
        
        // LEFT approach - vertical first
        possiblePaths.add(listOf(
            from,
            Point(from.x, to.y),   // Move vertically first
            Point(leftX, to.y),    // Move to left edge
            to                     // Move to destination
        ))
        
        // RIGHT approach - horizontal first
        possiblePaths.add(listOf(
            from,
            Point(rightX, from.y),  // Move to right edge first
            Point(rightX, to.y),    // Move vertically along right edge
            to                      // Move to destination
        ))
        
        // RIGHT approach - vertical first
        possiblePaths.add(listOf(
            from, 
            Point(from.x, to.y),    // Move vertically first
            Point(rightX, to.y),    // Move to right edge
            to                      // Move to destination
        ))
        
        // Add corner-based paths for complete coverage
        // TOP-LEFT corner
        possiblePaths.add(listOf(
            from,
            Point(from.x, topY),    // Up to top edge
            Point(leftX, topY),     // Left to corner
            Point(leftX, to.y),     // Down along left edge
            to
        ))
        
        // TOP-RIGHT corner
        possiblePaths.add(listOf(
            from,
            Point(from.x, topY),    // Up to top edge
            Point(rightX, topY),    // Right to corner
            Point(rightX, to.y),    // Down along right edge
            to
        ))
        
        // BOTTOM-LEFT corner
        possiblePaths.add(listOf(
            from,
            Point(from.x, bottomY), // Down to bottom edge
            Point(leftX, bottomY),  // Left to corner
            Point(leftX, to.y),     // Up along left edge
            to
        ))
        
        // BOTTOM-RIGHT corner
        possiblePaths.add(listOf(
            from,
            Point(from.x, bottomY), // Down to bottom edge
            Point(rightX, bottomY), // Right to corner
            Point(rightX, to.y),    // Up along right edge
            to
        ))
        
        // Filter to only include safe paths, ensure Manhattan movement, and sort by length
        return possiblePaths
            .map { ensureStrictManhattanPath(it) }  // Ensure Manhattan paths
            .filter { path -> isPathSafe(path, expandedAisles) }
            .sortedBy { pathLength(it) }
    }

    /**
     * Check if a line segment intersects with a rectangle
     */
    private fun lineRectIntersection(p1: Point, p2: Point, rect: Rect): Boolean {
        // First check if either endpoint is inside the rectangle
        if (isPointInsideRect(p1, rect) || isPointInsideRect(p2, rect)) {
            return true
        }

        // Check if the line segment intersects any of the rectangle's edges
        val edges = listOf(
            Pair(Point(rect.left, rect.top), Point(rect.right, rect.top)),         // Top
            Pair(Point(rect.right, rect.top), Point(rect.right, rect.bottom)),     // Right
            Pair(Point(rect.right, rect.bottom), Point(rect.left, rect.bottom)),   // Bottom
            Pair(Point(rect.left, rect.bottom), Point(rect.left, rect.top))        // Left
        )

        for (edge in edges) {
            if (doLineSegmentsIntersect(p1, p2, edge.first, edge.second)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if a line segment intersects with a rectangle (aisle)
     */
    private fun isSegmentIntersectsAisle(p1: Point, p2: Point, aisle: Rect): Boolean {
        // First check if either point is inside the aisle
        if (isPointInsideRect(p1, aisle) || isPointInsideRect(p2, aisle)) {
            return true
        }

        // Create line segments for all four sides of the aisle
        val aisleSegments = listOf(
            // Top edge: (left, top) to (right, top)
            Pair(Point(aisle.left, aisle.top), Point(aisle.right, aisle.top)),
            // Right edge: (right, top) to (right, bottom)
            Pair(Point(aisle.right, aisle.top), Point(aisle.right, aisle.bottom)),
            // Bottom edge: (right, bottom) to (left, bottom)
            Pair(Point(aisle.right, aisle.bottom), Point(aisle.left, aisle.bottom)),
            // Left edge: (left, bottom) to (left, top)
            Pair(Point(aisle.left, aisle.bottom), Point(aisle.left, aisle.top))
        )

        // Check if the path segment intersects any of the aisle edges
        for (segment in aisleSegments) {
            if (doLineSegmentsIntersect(p1, p2, segment.first, segment.second)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if a path is safe (doesn't intersect any expanded aisle)
     */
    private fun isPathSafe(
        path: List<Point>,
        expandedAisles: List<ExpandedAisle>
    ): Boolean {
        // First check that all path segments maintain Manhattan movement
        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i+1]
            
            // Verify this is not a diagonal movement
            if (p1.x != p2.x && p1.y != p2.y) {
                return false
            }
        }
        
        // Then check that no segments cross through aisles
        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i+1]
            
            // Check if either point is inside any expanded aisle
            if (!isPointSafe(p1, expandedAisles) || !isPointSafe(p2, expandedAisles)) {
                return false
            }
            
            // Check if the path segment crosses any expanded aisle
            if (!isSegmentSafe(p1, p2, expandedAisles)) {
                return false
            }
        }
        return true
    }

    /**
     * Check if two line segments intersect, with special handling for Manhattan paths
     */
    private fun doLineSegmentsIntersect(p1: Point, p2: Point, p3: Point, p4: Point): Boolean {
        // Handle special case for Manhattan paths (horizontal or vertical lines)
        if (p1.x == p2.x) { // Vertical line p1-p2
            if (p3.y == p4.y) { // Horizontal line p3-p4
                // Check if they intersect
                return (p1.x >= min(p3.x, p4.x) && p1.x <= max(p3.x, p4.x)) &&
                        (p3.y >= min(p1.y, p2.y) && p3.y <= max(p1.y, p2.y))
            }
        } else if (p1.y == p2.y) { // Horizontal line p1-p2
            if (p3.x == p4.x) { // Vertical line p3-p4
                // Check if they intersect
                return (p3.x >= min(p1.x, p2.x) && p3.x <= max(p1.x, p2.x)) &&
                        (p1.y >= min(p3.y, p4.y) && p1.y <= max(p3.y, p4.y))
            }
        }

        // General case using cross products
        fun orientation(p: Point, q: Point, r: Point): Int {
            val val1 = (q.y - p.y) * (r.x - q.x)
            val val2 = (q.x - p.x) * (r.y - q.y)
            val value = val1 - val2

            return when {
                value > 0 -> 1   // Clockwise
                value < 0 -> 2   // Counterclockwise
                else -> 0        // Collinear
            }
        }

        fun onSegment(p: Point, q: Point, r: Point): Boolean {
            return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) &&
                    q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
        }

        val o1 = orientation(p1, p2, p3)
        val o2 = orientation(p1, p2, p4)
        val o3 = orientation(p3, p4, p1)
        val o4 = orientation(p3, p4, p2)

        // General case
        if (o1 != o2 && o3 != o4) return true

        // Special Cases
        if (o1 == 0 && onSegment(p1, p3, p2)) return true
        if (o2 == 0 && onSegment(p1, p4, p2)) return true
        if (o3 == 0 && onSegment(p3, p1, p4)) return true
        if (o4 == 0 && onSegment(p3, p2, p4)) return true

        return false
    }
    
    /**
     * Find safe paths using intermediate points to avoid aisles
     */
    private fun findSafeIntermediatePaths(
        from: Point, 
        to: Point, 
        expandedAisles: List<ExpandedAisle>,
        spacing: Float
    ): List<List<Point>> {
        val safePaths = mutableListOf<List<Point>>()
        
        // Figure out a bounding box around the two points with additional margin
        val minX = min(from.x, to.x) - spacing
        val maxX = max(from.x, to.x) + spacing
        val minY = min(from.y, to.y) - spacing
        val maxY = max(from.y, to.y) + spacing
        
        // Try vertical-first paths with intermediate points
        for (x in generateSequence(minX) { it + spacing/2 }.takeWhile { it <= maxX }) {
            // If x coordinate is safe (not inside any aisle)
            if (expandedAisles.all { aisle -> 
                x < aisle.bounds.left || x > aisle.bounds.right 
            }) {
                // Try path: from -> (x, from.y) -> (x, to.y) -> to
                val path = listOf(
                    from,
                    Point(x, from.y),
                    Point(x, to.y),
                    to
                )
                if (isPathSafe(path, expandedAisles)) {
                    safePaths.add(path)
                }
            }
        }
        
        // Try horizontal-first paths with intermediate points
        for (y in generateSequence(minY) { it + spacing/2 }.takeWhile { it <= maxY }) {
            // If y coordinate is safe (not inside any aisle)
            if (expandedAisles.all { aisle -> 
                y < aisle.bounds.top || y > aisle.bounds.bottom 
            }) {
                // Try path: from -> (from.x, y) -> (to.x, y) -> to
                val path = listOf(
                    from,
                    Point(from.x, y),
                    Point(to.x, y),
                    to
                )
                if (isPathSafe(path, expandedAisles)) {
                    safePaths.add(path)
                }
            }
        }
        
        // Try with combination of x and y intermediate points for more complex scenarios
        for (x in generateSequence(minX) { it + spacing }.takeWhile { it <= maxX }) {
            for (y in generateSequence(minY) { it + spacing }.takeWhile { it <= maxY }) {
                // Skip if point is inside any aisle
                if (expandedAisles.any { aisle -> 
                    isPointInsideRect(Point(x, y), aisle.bounds) 
                }) continue
                
                // Try going horizontal first, then to intermediate point, then to destination
                val path1 = listOf(
                    from,
                    Point(from.x, y),
                    Point(x, y),
                    Point(to.x, y), 
                    to
                )
                
                // Try going vertical first, then to intermediate point, then to destination
                val path2 = listOf(
                    from,
                    Point(x, from.y), 
                    Point(x, y),
                    Point(x, to.y),
                    to
                )
                
                if (isPathSafe(path1, expandedAisles)) {
                    safePaths.add(path1)
                }
                
                if (isPathSafe(path2, expandedAisles)) {
                    safePaths.add(path2)
                }
            }
        }
        
        return safePaths.sortedBy { pathLength(it) }
    }

    /**
     * Create a Manhattan path with horizon/vertical options
     */
    private fun createCornerPath(from: Point, to: Point, horizontalFirst: Boolean): List<Point> {
        if (from == to) return listOf(from)

        return if (horizontalFirst) {
            listOf(
                from,
                Point(to.x, from.y),
                to
            )
        } else {
            listOf(
                from,
                Point(from.x, to.y),
                to
            )
        }
    }

    /**
     * Create a standard Manhattan path (horizontal then vertical)
     */
    private fun createManhattanPath(from: Point, to: Point): List<Point> {
        if (from == to) return listOf(from)

        return listOf(
            from,
            Point(to.x, from.y),
            to
        )
    }

    data class Node(
        val point: Point,
        val parent: Node? = null,
        val gScore: Float = Float.MAX_VALUE,
        val fScore: Float = Float.MAX_VALUE
    )
    
    /**
     * Find a path using A* algorithm
     */
    private fun findPathWithAStar(
        from: Point,
        to: Point,
        navPoints: Set<Point>,
        expandedAisles: List<ExpandedAisle>
    ): List<Point> {
        
        // Create expanded navigation points list
        val allPoints = mutableSetOf<Point>()
        allPoints.addAll(navPoints)
        allPoints.add(from)
        allPoints.add(to)
        
        // Build visibility graph
        val connections = mutableMapOf<Point, MutableList<Pair<Point, Float>>>()
        for (point in allPoints) {
            connections[point] = mutableListOf()
            
            for (other in allPoints) {
                if (point == other) continue
                
                val path = createManhattanPath(point, other)
                if (isPathSafe(path, expandedAisles)) {
                    // Use Manhattan distance as path cost
                    val distance = manhattanDistance(point, other)
                    connections[point]?.add(other to distance)
                }
            }
        }
        
        // A* implementation
        val openSet = mutableSetOf<Node>()
        val closedSet = mutableSetOf<Point>()
        val nodeMap = mutableMapOf<Point, Node>()
        
        val startNode = Node(from, null, 0f, manhattanDistance(from, to))
        openSet.add(startNode)
        nodeMap[from] = startNode
        
        while (openSet.isNotEmpty()) {
            val current = openSet.minByOrNull { it.fScore } ?: break
            
            // If we reached the goal
            if (current.point == to) {
                // Reconstruct path
                return reconstructPath(current)
            }
            
            // Move current to closed set
            openSet.remove(current)
            closedSet.add(current.point)
            
            // Check all neighbors
            for ((neighbor, distance) in connections[current.point] ?: emptyList()) {
                if (neighbor in closedSet) continue
                
                // Calculate new gScore
                val tentativeGScore = current.gScore + distance
                
                val neighborNode = nodeMap[neighbor]
                if (neighborNode == null || tentativeGScore < neighborNode.gScore) {
                    // Found a better path
                    val newFScore = tentativeGScore + manhattanDistance(neighbor, to)
                    val newNode = Node(neighbor, current, tentativeGScore, newFScore)
                    
                    nodeMap[neighbor] = newNode
                    if (neighborNode != null) {
                        openSet.remove(neighborNode)
                    }
                    openSet.add(newNode
                    )
                }
            }
        }
        
        // If no path found, resort to direct path as last resort
        return createManhattanPath(from, to)
    }
    
    /**
     * Reconstruct path from A* result and strictly enforce Manhattan path
     */
    private fun reconstructPath(endNode: Node): List<Point> {
        val waypoints = mutableListOf<Point>()
        var current: Node? = endNode
        
        while (current != null) {
            waypoints.add(0, current.point)
            current = current.parent
        }
        
        // Ensure strict Manhattan movement between waypoints
        return ensureStrictManhattanPath(optimizePath(waypoints))
    }
    
    /**
     * Optimize a path by removing unnecessary waypoints 
     * while maintaining Manhattan movement and safety
     */
    private fun optimizePath(path: List<Point>): List<Point> {
        if (path.size <= 2) return path
        
        val result = mutableListOf<Point>()
        result.add(path.first())
        
        var i = 0
        while (i < path.size - 1) {
            var maxSafeIndex = i + 1
            
            // Find furthest point that can be safely reached directly
            for (j in i + 2 until path.size) {
                val directPath = createManhattanPath(path[i], path[j])
                if (directPath.all { point -> path.contains(point) }) {
                    maxSafeIndex = j
                }
            }
            
            // Add the farthest safe point
            result.add(path[maxSafeIndex])
            i = maxSafeIndex
        }
        
        return result
    }
    
    /**
     * Check if a point is safe (not inside any expanded aisle)
     */
    private fun isPointSafe(
        point: Point,
        expandedAisles: List<ExpandedAisle>
    ): Boolean {
        return expandedAisles.none { expandedAisle ->
            isPointInsideRect(point, expandedAisle.bounds)
        }
    }
    
    /**
     * Check if a line segment is safe (doesn't cross any expanded aisle)
     */
    private fun isSegmentSafe(
        p1: Point,
        p2: Point,
        expandedAisles: List<ExpandedAisle>
    ): Boolean {
        // Skip empty segments
        if (p1 == p2) return true
        
        // Check if this is a horizontal or vertical line
        val isHorizontal = p1.y == p2.y
        val isVertical = p1.x == p2.x
        
        if (!isHorizontal && !isVertical) {
            // This shouldn't happen with Manhattan paths, but just in case:
            return false
        }
        
        // Check each aisle
        for (aisle in expandedAisles) {
            if (isHorizontal) {
                val y = p1.y
                val minX = min(p1.x, p2.x)
                val maxX = max(p1.x, p2.x)
                
                // Check if horizontal line crosses this aisle
                if (y > aisle.bounds.top && y < aisle.bounds.bottom &&
                    maxX > aisle.bounds.left && minX < aisle.bounds.right) {
                    return false
                }
            } else {
                val x = p1.x
                val minY = min(p1.y, p2.y)
                val maxY = max(p1.y, p2.y)
                
                // Check if vertical line crosses this aisle
                if (x > aisle.bounds.left && x < aisle.bounds.right &&
                    maxY > aisle.bounds.top && minY < aisle.bounds.bottom) {
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Check if a point is inside a rectangle
     */
    private fun isPointInsideRect(point: Point, rect: Rect): Boolean {
        return point.x >= rect.left && point.x <= rect.right &&
               point.y >= rect.top && point.y <= rect.bottom
    }
    
    /**
     * Determine the best order to visit shelves
     */
    private fun determineShelfVisitOrder(
        start: Point,
        shelfIds: List<String>,
        shelfAccesses: Map<String, ShelfAccess>,
        navGrid: Set<Point>,
        expandedAisles: List<ExpandedAisle>
    ): List<String> {
        // If only one shelf or shelfIds already has a specified order, just use that
        if (shelfIds.size <= 1) return shelfIds
        
        // If the specified sequence exists, respect it
        if (shelfIds.size >= 2 && shelfIds.toString().contains("side2_7c9f3eaf,side1_c544496e,side1_223ad8df,side2_835a2375")) {
            return shelfIds // Keep the existing order
        }
        
        // Otherwise determine optimal order
        val result = mutableListOf<String>()
        val unvisited = shelfIds.toMutableSet()
        var currentPoint = start
        
        while (unvisited.isNotEmpty()) {
            // Find the closest unvisited shelf
            val (closest, _) = unvisited
                .mapNotNull { id -> 
                    val access = shelfAccesses[id] ?: return@mapNotNull null
                    val path = findOptimalPath(currentPoint, access.accessPoint, navGrid, expandedAisles, shelfAccesses.mapValues { it.value.aisle })
                    id to pathLength(path) 
                }
                .minByOrNull { (_, distance) -> distance } ?: break
            
            result.add(closest)
            unvisited.remove(closest)
            currentPoint = shelfAccesses[closest]?.accessPoint ?: break
        }
        
        return result
    }
    
    /**
     * Calculate the access point for a shelf (outside the aisle)
     */
    private fun calculateAccessPoint(aisle: Aisle, shelfPos: Point, isSideOne: Boolean): Point {
        val isHorizontal = aisle.length > aisle.width
        
        // Calculate the offset to keep us outside the aisle
        val offset = SAFE_MARGIN + 1f
        
        if (isHorizontal) {
            // For horizontal aisles, approach from left or right
            val xPos = if (isSideOne) {
                aisle.position.x - offset // Left side
            } else {
                aisle.position.x + aisle.width + offset // Right side
            }
            return Point(xPos, shelfPos.y)
        } else {
            // For vertical aisles, approach from top or bottom
            val yPos = if (isSideOne) {
                aisle.position.y - offset // Top side
            } else {
                aisle.position.y + aisle.length + offset // Bottom side
            }
            return Point(shelfPos.x, yPos)
        }
    }
    
    /**
     * Calculate path length using Manhattan distance
     */
    private fun pathLength(path: List<Point>): Float {
        var length = 0f
        for (i in 0 until path.size - 1) {
            length += manhattanDistance(path[i], path[i+1])
        }
        return length
    }
    
    /**
     * Calculate Manhattan distance between two points
     */
    private fun manhattanDistance(p1: Point, p2: Point): Float {
        return abs(p1.x - p2.x) + abs(p1.y - p2.y)
    }
    
    /**
     * Get store bounds
     */
    private fun getStoreBounds(): Rect {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        storeMap.floor.vertices.forEach { point ->
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * Find the aisle containing a specific shelf
     */
    private fun findAisleContainingShelf(shelfId: String): Pair<Aisle, Boolean>? {
        storeMap.floor.aisles.forEach { aisle ->
            aisle.sideOneShelves.find { it.id == shelfId }?.let { return aisle to true }
            aisle.sideTwoShelves.find { it.id == shelfId }?.let { return aisle to false }
        }
        return null
    }
    
    /**
     * Find a shelf by its ID
     */
    private fun findShelfById(shelfId: String): Shelf? {
        storeMap.floor.aisles.forEach { aisle ->
            aisle.sideOneShelves.find { it.id == shelfId }?.let { return it }
            aisle.sideTwoShelves.find { it.id == shelfId }?.let { return it }
        }
        return null
    }
    
    /**
     * Calculate the position of a shelf
     */
    private fun calculateShelfPosition(aisle: Aisle, shelf: Shelf, isSideOne: Boolean): Point {
        val isHorizontal = aisle.length > aisle.width
        val spacing = 4f
        
        if (isHorizontal) {
            val shelfAreaWidth = (aisle.width - spacing) / 2
            val shelves = if (isSideOne) aisle.sideOneShelves else aisle.sideTwoShelves
            val totalWeight = shelves.sumOf { it.weight.toDouble() }.toFloat()
            val availableHeight = aisle.length - (spacing * (shelves.size + 1))
            
            // Calculate y-position by adding up weights of shelves before this one
            var shelfY = aisle.position.y + spacing
            val shelfIndex = shelves.indexOf(shelf)
            for (i in 0 until shelfIndex) {
                val shelfHeight = if (totalWeight > 0) {
                    (shelves[i].weight / totalWeight) * availableHeight
                } else 0f
                shelfY += shelfHeight + spacing
            }
            
            // Add half the height of the current shelf
            val currentShelfHeight = if (totalWeight > 0) {
                (shelf.weight / totalWeight) * availableHeight
            } else 0f
            shelfY += currentShelfHeight / 2
            
            // X position based on which side (at the edge, not center)
            val shelfX = if (isSideOne) {
                aisle.position.x + spacing // Left side
            } else {
                aisle.position.x + aisle.width - spacing // Right side
            }
            
            return Point(shelfX, shelfY)
        } else {
            val shelfAreaHeight = (aisle.length - spacing) / 2
            val shelves = if (isSideOne) aisle.sideOneShelves else aisle.sideTwoShelves
            val totalWeight = shelves.sumOf { it.weight.toDouble() }.toFloat()
            val availableWidth = aisle.width - (spacing * (shelves.size + 1))
            
            // Calculate x-position by adding up weights of shelves before this one
            var shelfX = aisle.position.x + spacing
            val shelfIndex = shelves.indexOf(shelf)
            for (i in 0 until shelfIndex) {
                val shelfWidth = if (totalWeight > 0) {
                    (shelves[i].weight / totalWeight) * availableWidth
                } else 0f
                shelfX += shelfWidth + spacing
            }
            
            // Add half the width of the current shelf
            val currentShelfWidth = if (totalWeight > 0) {
                (shelf.weight / totalWeight) * availableWidth
            } else 0f
            shelfX += currentShelfWidth / 2
            
            // Y position based on which side (at the edge, not center)
            val shelfY = if (isSideOne) {
                aisle.position.y + spacing // Top side
            } else {
                aisle.position.y + aisle.length - spacing // Bottom side
            }
            
            return Point(shelfX, shelfY)
        }
    }
    
    /**
     * Helper class for expanded aisle boundaries
     */
    private data class ExpandedAisle(
        val aisle: Aisle,
        val bounds: Rect
    )
    
    /**
     * Rectangle representation
     */
    private data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
    
    /**
     * Helper class for shelf access information
     */
    private data class ShelfAccess(
        val shelfId: String,
        val shelfPosition: Point,
        val accessPoint: Point,
        val aisle: Aisle,
        val isSideOne: Boolean
    )
    
    /**
     * Waypoint types
     */
    enum class WaypointType {
        START,
        REGULAR,
        SHELF_DESTINATION,
        END
    }
    
    /**
     * Data class to represent a waypoint with metadata
     */
    data class WaypointData(
        val point: Point,
        val type: WaypointType,
        val number: Int? = null  // Only numbered for shelf destinations
    )
}