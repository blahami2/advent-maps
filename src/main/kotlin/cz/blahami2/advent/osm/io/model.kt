package cz.blahami2.advent.osm.io

import cz.blahami2.advent.osm.model.Type
import org.openstreetmap.osmosis.osmbinary.Osmformat


data class OsmTag(val key: String, val value: String)


data class OsmNode(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: List<OsmTag>,
    val info: Osmformat.Info,
    val tile: Int,
)

data class OsmWay(
    val id: Long,
    val name: String,
    val tags: List<OsmTag>,
    val refs: List<Long>,
    val info: Osmformat.Info,
    val type: Type,
)

data class OsmMember(
    val type: Osmformat.Relation.MemberType,
    val ref: Long,
    val role: String,
)

data class OsmRelation(
    val id: Long,
    val name: String,
    val tags: List<OsmTag>,
    val members: List<OsmMember>,
    val info: Osmformat.Info,
    val type: Type,
)
