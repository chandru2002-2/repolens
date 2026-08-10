plugins {
    `java-library`
}

dependencies {
    api(project(":repolens-core"))

    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}
