plugins { java }
repositories { mavenCentral() }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
sourceSets {
    main {
        java.setSrcDirs(listOf("../../extensions/music/src/main/java"))
        java.include("app/morphe/extension/music/patches/lyrics/ai/*.java")
        java.exclude("**/ChatGptSession.java", "**/SecureSessionStore.java", "**/LyricsTranslationEngine.java")
    }
    test { java.setSrcDirs(listOf("../../extensions/music/src/test/java")) }
}
dependencies {
    compileOnly("org.json:json:20240303")
    testImplementation("org.json:json:20240303")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
