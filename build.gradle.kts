plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "cz.blahami2"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // OSM Parsing Libraries
//    implementation("de.westnordost:osmapi:5.1")
    implementation("org.openstreetmap.osmosis:osmosis-core:0.48.3")
    implementation("org.openstreetmap.osmosis:osmosis-osm-binary:0.48.3")
    implementation("org.locationtech.jts:jts-core:1.20.0")

    // Logging (optional but recommended)
    implementation("org.slf4j:slf4j-simple:1.7.30")

    // Image Processing Libraries
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
