plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "me.walkerknapp"
version = "0.0.1"

gradlePlugin {
    plugins {
        register("serveStatic") {
            id = "me.walkerknapp.serve-static-resources"
            implementationClass = "me.walkerknapp.servestatic.ServeStaticPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())

    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.6.8")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.vertx:vertx-web:3.4.2")
}
