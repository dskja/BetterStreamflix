plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
}

tasks.named("detekt").configure {
    reports {
        html.required.set(true)
        sarif.required.set(false)
        xml.required.set(false)
        md.required.set(false)
    }
}
