package cz.blahami2.advent.osm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.openstreetmap.osmosis.osmbinary.BinaryParser
import org.openstreetmap.osmosis.osmbinary.Osmformat
import org.openstreetmap.osmosis.osmbinary.file.BlockInputStream
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO


fun downloadOSMData() {
    val osmUrl = "https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf"
    val outputPath = "data/czech-republic-latest.osm.pbf"
    println("Downloading OSM data from $osmUrl...")
    URL(osmUrl).openStream().use { input ->
        Files.copy(input, Paths.get(outputPath))
    }
    println("Download completed: $outputPath")
}

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

data class OsmTag(val key: String, val value: String)

private class OsmBinaryParser : BinaryParser() {

    val nodes = mutableMapOf<Long, OsmNode>()
    val ways = mutableMapOf<Long, OsmWay>()
    val relations = mutableMapOf<Long, OsmRelation>()

    private fun List<OsmTag>.parseType(): Type {
        return if (getTag("waterway") == "river") {
            Type.RIVER
        } else if (getTag("natural") == "mountain_range" || getTag("natural") == "ridge"){
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
        println("Parsing completed")
    }

    override fun parse(hb: Osmformat.HeaderBlock?) {
        println("header: $hb")
    }

    companion object {
        val logger = org.slf4j.LoggerFactory.getLogger(OsmBinaryParser::class.java)
    }
}

private fun List<OsmTag>.getTag(key: String) = this.firstOrNull { it.key == key }?.value

val geometryFactory = GeometryFactory()

private fun OsmWay.toWay(nodes: Map<Long, OsmNode>): Way {
    val nodes = refs.mapNotNull { nodes[it] }.map { node ->
        Node(node.lon, node.lat, node.tags)
    }
    val lineString = geometryFactory.createLineString(nodes.toTypedArray())
    return Way(nodes, tags, lineString, type)
}

private fun OsmRelation.toWays(ways: Map<Long, OsmWay>, nodes: Map<Long, OsmNode>): List<Way> {
    return members.map { member -> member.ref }.mapNotNull { ways[it] }.map { it.toWay(nodes) }
}

class AdventMaps

data class Node(
    val lat: Double,
    val lon: Double,
    val tags: List<OsmTag>,
) : Coordinate(lat, lon)

data class Way(
    val nodes: List<Node>,
    val tags: List<OsmTag>,
    val lineString: LineString,
    val type: Type,
)

fun main() {
    val parser = OsmBinaryParser()
    val resourceAsStream = FileInputStream(File("data/czech-republic-latest.osm.pbf"))
    val stream = BlockInputStream(resourceAsStream, parser)
    stream.process()
    val borders = parser.relations.values.filter { it.type == Type.COUNTRY }
        .flatMap { it.toWays(parser.ways, parser.nodes) }
    for (type in Type.entries.filter { it != Type.OTHER }) {
        val relations = parser.relations.values.filter { it.type == type }
        val ways = relations.flatMap { relation -> relation.toWays(parser.ways, parser.nodes) } +
                parser.ways.filter { it.value.type == type }.map { it.value.toWay(parser.nodes) }
        renderToImage(borders + ways, borders, ways, "${type.name}.png", 1600, 1000)
    }
}


fun renderToImage(
    allWays: Iterable<Way>,
    borders: Iterable<Way>,
    rivers: Iterable<Way>,
    imagePath: String,
    width: Int,
    height: Int
) {

    // Calculate bounding box
    val envelope = Envelope()
    allWays.forEach { way ->
        envelope.expandToInclude(way.lineString.envelopeInternal)
    }

    // Projection function (simple equirectangular)
    fun project(lon: Double, lat: Double): Pair<Int, Int> {
        val x = ((lon - envelope.minX) / (envelope.maxX - envelope.minX)) * (width - 40) + 20
        val y = ((envelope.maxY - lat) / (envelope.maxY - envelope.minY)) * (height - 40) + 20
        return Pair(x.toInt(), y.toInt())
    }

    // Create a blank image
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g: Graphics2D = image.createGraphics()

    // Enable Anti-Aliasing
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON
    )

    // Fill background with white
    g.color = Color.WHITE
    g.fillRect(0, 0, width, height)

    fun Iterable<Way>.print(color: Color, stroke: Float = 1f) {
        g.color = color
        g.stroke = BasicStroke(stroke)
        forEach { way ->
            val coords = way.lineString.coordinates
            if (coords.size < 2) return@forEach
            for (i in 0 until coords.size - 1) {
                val (x1, y1) = project(coords[i].x, coords[i].y)
                val (x2, y2) = project(coords[i + 1].x, coords[i + 1].y)
                g.drawLine(x1, y1, x2, y2)
            }
        }
    }

    borders.print(Color.BLACK, 1f)
    rivers.print(Color.BLUE)

    g.dispose()

    // Save the image
    val outputFile = File(imagePath)
    outputFile.parentFile?.mkdirs()
    ImageIO.write(image, "png", outputFile)
    println("Rivers rendered to $imagePath")
}
