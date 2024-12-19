package cz.blahami2.advent.osm.view

import cz.blahami2.advent.osm.model.Way
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class Renderer(
    private val width: Int,
    private val height: Int
) {
    private val geometryFactory = GeometryFactory()

    fun renderToImage(
        borders: Iterable<Way>,
        elements: Iterable<Way>,
        imagePath: String,
    ) {
        // Calculate bounding box
        val envelope = Envelope()
        (borders + elements).forEach { way ->
            val lineString = geometryFactory.createLineString(way.nodes.toTypedArray())
            envelope.expandToInclude(lineString.envelopeInternal)
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
                val lineString = geometryFactory.createLineString(way.nodes.toTypedArray())
                val coords = lineString.coordinates
                if (coords.size < 2) return@forEach
                for (i in 0 until coords.size - 1) {
                    val (x1, y1) = project(coords[i].x, coords[i].y)
                    val (x2, y2) = project(coords[i + 1].x, coords[i + 1].y)
                    g.drawLine(x1, y1, x2, y2)
                }
            }
        }

        borders.print(Color.BLACK, 1f)
        elements.print(Color.BLUE)

        g.dispose()

        // Save the image
        val outputFile = File(imagePath)
        outputFile.parentFile?.mkdirs()
        ImageIO.write(image, "png", outputFile)
        logger.info("Rivers rendered to $imagePath")
    }

    companion object {
        val logger = org.slf4j.LoggerFactory.getLogger(Renderer::class.java)
    }
}