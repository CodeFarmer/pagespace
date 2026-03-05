plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../src/main/kotlin")
        kotlin.include(
            "io/gluth/pagespace/domain/**",
            "io/gluth/pagespace/backend/**",
            "io/gluth/pagespace/layout/**",
            "io/gluth/pagespace/presenter/**"
        )
    }
    test {
        kotlin.srcDir("../src/test/kotlin")
        kotlin.include(
            "io/gluth/pagespace/domain/**",
            "io/gluth/pagespace/backend/**",
            "io/gluth/pagespace/layout/**",
            "io/gluth/pagespace/presenter/**"
        )
    }
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
