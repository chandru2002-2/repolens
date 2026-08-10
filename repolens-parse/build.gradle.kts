plugins {
    `java-library`
}

dependencies {
    api(project(":repolens-core"))

    implementation("ch.usi.si.seart:java-tree-sitter:1.12.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
