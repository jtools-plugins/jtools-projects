import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.intellij.platform") version "2.7.2"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.lhstack"
version = "0.0.1"


repositories {
    intellijPlatform {
        defaultRepositories()
    }
    mavenLocal()
    maven("https://maven.aliyun.com/repository/public/")
    mavenCentral()
}
dependencies {
//    implementation("com.alibaba.fastjson2:fastjson2:2.0.58")
//    implementation(files("C:/Users/lhstack/.jtools/sdk/sdk.jar"))
//    implementation(files("C:/Users/1/.jtools/sdk/sdk.jar"))
    implementation(files("C:/Users/lhstack/.jtools/sdk/sdk.jar"))
    implementation("com.alibaba.fastjson2:fastjson2:2.0.58")
    intellijPlatform{
        intellijIdeaCommunity("2025.2")
        bundledPlugin("com.intellij.platform.images")
    }
}


tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
        options.encoding = "UTF-8"
    }
    withType<JavaExec> {
        jvmArgs("-Dfile.encoding=UTF-8")
    }

    withType<Jar>(){
        archiveBaseName = "jtools-projects"
    }

    withType<ShadowJar> {
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer::class.java)
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer::class.java)
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.XmlAppendingTransformer::class.java)
        exclude("META-INF/MANIFEST.MF","META-INF/*.SF","META-INF/*.DSA")
        dependencies {
            exclude(dependency("com.jetbrains.*:.*:.*"))
            exclude(dependency("org.jetbrains.*:.*:.*"))
        }
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions{
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs = listOf("-Xjvm-default=all")
        }
    }


}
tasks.test {
    useJUnitPlatform()
}