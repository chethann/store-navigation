package com.pincode.storenav.model

import kotlinx.serialization.Serializable

@Serializable
data class Point(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

@Serializable
data class StoreFloor(
    val id: String,
    val vertices: List<Point>,
    val aisles: List<Aisle> = emptyList()
)

@Serializable
data class Aisle(
    val id: String,
    val position: Point,
    val width: Float,
    val length: Float,
    val height: Float = 200f,
    val sideOneShelves: List<Shelf> = emptyList(),
    val sideTwoShelves: List<Shelf> = emptyList()
)

@Serializable
data class Shelf(
    val id: String,
    val weight: Float, // Weight determines the space taken by the shelf
    val rows: List<ShelfRow> = emptyList()
)

@Serializable
data class ShelfRow(
    val id: String,
    val position: Point,
    val width: Float,
    val length: Float,
    val height: Float,
    val items: List<StoreItem> = emptyList()
)

@Serializable
data class StoreItem(
    val id: String,
    val name: String,
    val position: Point,
    val width: Float,
    val length: Float,
    val height: Float
)

@Serializable
data class StoreMap(
    val floor: StoreFloor,
    val scale: Float = 1f
) 