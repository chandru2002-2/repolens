plugins {
    `java-library`
    application
}

dependencies {
    implementation(project(":repolens-core"))
    implementation(project(":repolens-ingest"))
    implementation(project(":repolens-parse"))
    implementation(project(":repolens-analyzers"))
    implementation(project(":repolens-api-model"))

    implementation("io.javalin:javalin:6.4.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("io.repolens.web.RepoLensWebMain")
}
