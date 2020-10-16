import java.net.URI

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "me.walkerknapp"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

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

val sourceJar by tasks.creating(Jar::class) {
    from(sourceSets.main.get().allJava)
    this.archiveClassifier.set("sources")
}

val javadocJar by tasks.creating(Jar::class) {
    dependsOn("javadoc")
    from(tasks.javadoc.get().destinationDir)
    this.archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("gradle-serve-static-resources") {
            from(components["java"])
            artifact(sourceJar)
            artifact(javadocJar)

            groupId = project.group as String
            artifactId = "gradle-serve-static-resources"
            version = project.version as String?

            pom {
                name.set(artifactId)
                description.set("A gradle plugin that adds generated code to a given build (usually a Node.JS build) to serve generated static resources.")
                url.set("https://github.com/WalkerKnapp/gradle-serve-static-resources")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("WalkerKnapp")
                        name.set("Walker Knapp")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/WalkerKnapp/gradle-serve-static-resources.git")
                    developerConnection.set("scm:git:git@github.com:WalkerKnapp/gradle-serve-static-resources.git")
                    url.set("https://github.com/WalkerKnapp/gradle-serve-static-resources")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/WalkerKnapp")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
