// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(descopeLibs.plugins.nexus.publish)
    alias(descopeLibs.plugins.android.library) apply false
    alias(descopeLibs.plugins.kotlin.android) apply false
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId = "20675571e61bf1" //can reduce execution time by even 10 seconds
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username = System.getenv("MAVEN_USERNAME")
            password = System.getenv("MAVEN_PASSWORD")
        }
    }
}
