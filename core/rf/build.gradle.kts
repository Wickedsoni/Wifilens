plugins {
    id("wifilens.jvm.library")
}

dependencies {
    api(project(":core:model")) // Vec2, GridPlan, ... are part of the RF API surface

    testImplementation(kotlin("test"))
}
