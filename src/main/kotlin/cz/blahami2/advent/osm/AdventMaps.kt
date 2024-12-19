package cz.blahami2.advent.osm

import cz.blahami2.advent.osm.io.MapLoader
import cz.blahami2.advent.osm.io.OsmTag
import cz.blahami2.advent.osm.view.Renderer
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths


fun downloadOSMData() {
    val osmUrl = "https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf"
    val outputPath = "data/czech-republic-latest.osm.pbf"
    println("Downloading OSM data from $osmUrl...")
    URL(osmUrl).openStream().use { input ->
        Files.copy(input, Paths.get(outputPath))
    }
    println("Download completed: $outputPath")
}

fun List<OsmTag>.getTag(key: String) = this.firstOrNull { it.key == key }?.value

class AdventMaps

fun main() {
    val map = MapLoader().loadMap("data/czech-republic-latest.osm.pbf")
    val renderer = Renderer(1600, 1000)
    map.relations.forEach { (type, relations) ->
        renderer.renderToImage(map.borders(), relations.flatMap { it.ways }, "build/${type.name}.png")
    }
}
