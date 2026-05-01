group = "org.example"
version = "1.0-SNAPSHOT"

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("jacoco")  // Add this line to enable JaCoCo plugin
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.13"  // Use the latest version
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("**/s3a/model/**")
            }
        })
    )
}

// Configure coverage verification rules
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "BUNDLE"
            // Rule for overall coverage
            limit {
                minimum = "0.60".toBigDecimal()  // Requires 60% overall coverage
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // lambda
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")

    // junit
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // immutables
    compileOnly("org.immutables:value:2.9.3")
    annotationProcessor("org.immutables:value:2.9.3")

    // lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // log4j2
    implementation("org.apache.logging.log4j:log4j-api:2.25.4")
    implementation("org.apache.logging.log4j:log4j-core:2.25.4")

    // SLF4J
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")

    // dynamodb
    implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.778")

    // dagger
    implementation("com.google.dagger:dagger:2.50")
    annotationProcessor("com.google.dagger:dagger-compiler:2.50")

    implementation(platform("software.amazon.awssdk:bom:2.31.72"))
    implementation("software.amazon.awssdk:aws-core")
    // arn
    implementation("software.amazon.awssdk:arns")

    // s3 control
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:s3control")

    // apache commons
    implementation("org.apache.commons:commons-text:1.15.0")

    //sts
    implementation("software.amazon.awssdk:sts")

    // cloudwatch
    implementation("software.amazon.awssdk:cloudwatch")

    // kms
    implementation("software.amazon.awssdk:kms")

    // policy builder
    implementation("software.amazon.awssdk:iam-policy-builder")

    // gson
    implementation("com.google.code.gson:gson:2.10.1")

    // mockito
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    //dynamodb local
    testImplementation("com.amazonaws:DynamoDBLocal:2.6.0")

    // Step functions
    implementation("software.amazon.awssdk:sfn")
    
    // Glue
    implementation("software.amazon.awssdk:glue")
}

configurations.all {
    resolutionStrategy {
        force("com.fasterxml.jackson.core:jackson-core:2.18.3")
        force("com.fasterxml.jackson.core:jackson-databind:2.18.3")
        force("com.fasterxml.jackson.core:jackson-annotations:2.18.3")

        force("io.netty:netty-codec:4.1.132.Final")
        force("io.netty:netty-codec-http:4.1.132.Final")
        force("io.netty:netty-codec-http2:4.1.132.Final")
        force("io.netty:netty-handler:4.1.132.Final")
        force("io.netty:netty-buffer:4.1.132.Final")
        force("io.netty:netty-common:4.1.132.Final")
        force("io.netty:netty-transport:4.1.132.Final")
        force("io.netty:netty-resolver:4.1.132.Final")
        force("io.netty:netty-transport-native-unix-common:4.1.132.Final")

        force("software.amazon.ion:ion-java:1.11.9")

        force("com.google.guava:guava:33.4.0-jre")

        force("org.eclipse.jetty:jetty-http:12.0.32")
        force("org.eclipse.jetty:jetty-server:12.0.32")
        force("org.eclipse.jetty:jetty-io:12.0.32")
        force("org.eclipse.jetty:jetty-util:12.0.32")
    }
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("all")
        mergeServiceFiles()
        dependsOn("test")  // Ensure tests run before shadowJar
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    useJUnitPlatform()
}
