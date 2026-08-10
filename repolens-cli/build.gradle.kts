plugins {
    application
}

dependencies {
    implementation(project(":repolens-core"))
    implementation(project(":repolens-ingest"))
    implementation(project(":repolens-parse"))
    implementation(project(":repolens-analyzers"))
    implementation(project(":repolens-api-model"))
    implementation(project(":repolens-web"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
}

application {
    mainClass.set("io.repolens.cli.RepoLensCli")
}
