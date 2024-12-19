package cz.blahami2.advent.osm.model

import cz.blahami2.advent.osm.io.OsmTag
import org.locationtech.jts.geom.Coordinate
import kotlin.collections.Map

data class StateMap(
    val relations : Map<Type, List<Relation>>,
) {
    fun borders() = relations[Type.COUNTRY]?.flatMap { it.ways } ?: emptyList()
}

data class Node(
    val lat: Double,
    val lon: Double,
    val tags: List<OsmTag>,
) : Coordinate(lat, lon)

data class Way(
    val nodes: List<Node>,
    val tags: List<OsmTag>,
    val type: Type,
)

data class Relation(
    val name: String,
    val ways: List<Way>,
    val tags: List<OsmTag>,
    val type: Type,
)

enum class Type {
    RIVER,
    MOUNTAIN,
    OTHER,
    COUNTRY,
    REGION,
    DISTRICT,
    CITY,
    OTHER_ADMINISTRATIVE,
}