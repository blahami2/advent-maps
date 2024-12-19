package cz.blahami2.advent.osm.io

import cz.blahami2.advent.osm.getTag
import cz.blahami2.advent.osm.model.*
import org.openstreetmap.osmosis.osmbinary.BinaryParser
import org.openstreetmap.osmosis.osmbinary.Osmformat
import org.openstreetmap.osmosis.osmbinary.file.BlockInputStream
import java.io.File
import java.io.FileInputStream

class MapLoader {

    fun loadMap(path: String): StateMap {
        val existingNodes = mutableMapOf<Long, Node>()
        val parser = OsmBinaryParser()
        val resourceAsStream = FileInputStream(File(path))
        val stream = BlockInputStream(resourceAsStream, parser)
        stream.process()
        val relationsByType = Type.entries.filter { it != Type.OTHER }.associateWith { type ->
            val osmRelations = parser.relations.values.filter { it.type == type }

            val relations = osmRelations.map { relation ->
                val ways = relation.members.mapNotNull { member -> parser.ways[member.ref] }
                    .map { it.toWay(existingNodes, parser.nodes) }
                Relation(relation.name, ways, relation.tags, relation.type)
            }
            // remove already used ways, then ways without relations will be left
            val wayMap = parser.ways.filter { it.value.type == type }.toMutableMap()
            osmRelations.forEach { relation ->
                relation.members.forEach { member -> wayMap.remove(member.ref) }
            }
            // use remaining ways with names
            val relationsFromWays = wayMap.values.map { way ->
                way.tags.getTag("name")?.let { name ->
                    way.toWay(existingNodes, parser.nodes)
                        .let { way -> Relation(name, listOf(way), way.tags, way.type) }
                }
            }
            (relations + relationsFromWays).filterNotNull()
        }
        return StateMap(relations = relationsByType)
    }

    private fun OsmWay.toWay(nodeCache: MutableMap<Long, Node>, nodes: Map<Long, OsmNode>): Way {
        val nodes = refs.mapNotNull { nodes[it] }.map { node ->
            nodeCache.getOrPut(node.id) {
                Node(node.lon, node.lat, node.tags)
            }
        }
        return Way(nodes, tags, type)
    }
}

private class OsmBinaryParser : BinaryParser() {

    val nodes = mutableMapOf<Long, OsmNode>()
    val ways = mutableMapOf<Long, OsmWay>()
    val relations = mutableMapOf<Long, OsmRelation>()

    private fun List<OsmTag>.parseType(): Type {
        return if (getTag("waterway") == "river") {
            Type.RIVER
        } else if (getTag("natural") == "mountain_range" || getTag("natural") == "ridge") {
            Type.MOUNTAIN
        } else if (getTag("boundary") == "administrative") {
            val adminLevel = getTag("admin_level")?.toIntOrNull() ?: 0
            when (adminLevel) {
                2 -> Type.COUNTRY
                6 -> Type.REGION
                7 -> Type.DISTRICT
                8 -> Type.CITY
                else -> Type.OTHER_ADMINISTRATIVE
            }
        } else {
            Type.OTHER
        }
    }

    private fun List<OsmTag>.parseName(): String = getTag("name") ?: "Unknown"

    override fun parseRelations(list: List<Osmformat.Relation>) {
        list.forEach { relation ->
            val tags = (0 until relation.keysCount).map { i ->
                OsmTag(getStringById(relation.keysList[i]), getStringById(relation.valsList[i]))
            }
            val type = tags.parseType()
            if (type != Type.OTHER) {
                var memberId = 0L
                val members = (0 until relation.memidsCount).map { i ->
                    memberId += relation.memidsList[i]
                    val role = getStringById(relation.rolesSidList[i])
                    OsmMember(Osmformat.Relation.MemberType.forNumber(relation.typesList[i].number), memberId, role)
                }
                val info = relation.info
                this.relations[relation.id] = OsmRelation(relation.id, tags.parseName(), tags, members, info, type)
            }
        }
    }

    override fun parseWays(ways: List<Osmformat.Way>) {
        ways.forEach { way ->
            val tags = (0 until way.keysCount).map { i ->
                val tag = OsmTag(getStringById(way.keysList[i]), getStringById(way.valsList[i]))
                tag
            }
            val type = tags.parseType()
            if (type != Type.OTHER) {
                var lastRef = 0L
                val refs = (0 until way.refsCount).map { i ->
                    lastRef += way.refsList[i]
                    lastRef
                }
                val info = way.info
                this.ways[way.id] = OsmWay(way.id, tags.parseName(), tags, refs, info, type)
            }
        }
    }

    override fun parseNodes(nodes: List<Osmformat.Node>) {
        nodes.forEach { node ->
            val tags = (0 until node.keysCount).map { i ->
                OsmTag(getStringById(node.keysList[i]), getStringById(node.valsList[i]))
            }
            val info = node.info
            this.nodes[node.id] = OsmNode(node.id, parseLat(node.lat), parseLon(node.lon), tags, info, 1)
        }
    }

    override fun parseDense(nodes: Osmformat.DenseNodes) {
        var id = 0L
        var lat = 0L
        var lon = 0L
        var keyValsIdx = 0
        val info = Osmformat.Info.getDefaultInstance()
        val tags = mutableListOf<OsmTag>()

        nodes.idList.forEachIndexed { index, nodeIdDelta ->
            id += nodeIdDelta
            lat += nodes.latList[index]
            lon += nodes.lonList[index]
            if (nodes.keysValsCount > 0) {
                while (nodes.keysValsList[keyValsIdx] != 0) {
                    val keyId = nodes.keysValsList[keyValsIdx++]
                    val valueId = nodes.keysValsList[keyValsIdx++]
                    tags.add(OsmTag(getStringById(keyId), getStringById(valueId)))
                }
                keyValsIdx++ // skip the 0 delimiter
            }
            this.nodes[id] = OsmNode(id, parseLat(lat), parseLon(lon), tags, info, index)
        }
    }

    override fun complete() {
        logger.info("Parsing completed")
    }

    override fun parse(hb: Osmformat.HeaderBlock?) {
        logger.info("header: $hb")
    }

    companion object {
        val logger = org.slf4j.LoggerFactory.getLogger(OsmBinaryParser::class.java)
    }
}
