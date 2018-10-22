package com.adamratzman.jpm.cache

import com.adamratzman.jpm.gson
import java.io.File

data class JPackage(val executablePath: String, val directoryName: String, val jPackageInfo: JPackageInfo)

data class UserPackageInfo(val name: String, val description: String, val author: String, val version: String) {
    override fun toString() = "$name by $author" + (if (version != "N/A") " (Version $version)" else "")
}

/**
 * @param packageSourcePath Where this package came from - either a local path string or a url
 */
data class JPackageInfo(val userDefinedInfo: UserPackageInfo, val packageInstallDate: Long,
                        val sourceLastEditDate: Long, val packageSourcePath: String)

fun buildCache(jpmPath: String): List<JPackage> {
    val jpmDirectory = File("$jpmPath/packages")
    if (!jpmDirectory.exists()) jpmDirectory.mkdirs()
    val cache = mutableListOf<JPackage>()
    jpmDirectory.listFiles().forEach { packageDirectory ->
        var valid = true
        val files = packageDirectory.listFiles()
        if (files == null || files.find { it.extension.endsWith("jar") } == null || files.find { it.name == "jpm.json" } == null) valid = false
        else {
            try {
                val info = gson.fromJson(files.first { it.name == "jpm.json" }.readText(), JPackageInfo::class.java)
                if (info == null) valid = false
                else cache.add(JPackage(files.first { it.extension.endsWith("jar") }.absolutePath, packageDirectory.name, info))
            } catch (e: Exception) {
                valid = false
            }
        }
        if (!valid) packageDirectory.deleteRecursively()
    }
    return cache
}
