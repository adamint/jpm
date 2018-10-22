package com.adamratzman.jpm.install

import com.adamratzman.jpm.JPM
import com.adamratzman.jpm.cache.JPackageInfo
import com.adamratzman.jpm.cache.UserPackageInfo
import com.adamratzman.jpm.exec
import com.adamratzman.jpm.gson
import com.adamratzman.jpm.hasGradle
import com.adamratzman.jpm.utils.writeObjectToFile
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

/**
 * @return pair of the new directory location and last commit/edit time of the repository/source code
 */
fun unpackIntoTmp(path: String, jpm: JPM): Pair<String, Long> {
    println("Checking Gradle installation...")
    if (!hasGradle(jpm)) throw CliktError("Gradle isn't available")
    else println("Gradle is installed. Continuing..")

    val jpmTmpDirectory = File(jpm.jpmPath + "/tmp")
    println("Cleaning up temporary files...")
    if (jpmTmpDirectory.exists()) jpmTmpDirectory.deleteRecursively()
    jpmTmpDirectory.mkdir()
    if (path.startsWith("git") || path.startsWith("http")) {
        println("Trying to clone repository..")
        val currentTmpFiles = jpmTmpDirectory.listFiles()
        // this needs to be cloned and compared to the current file list so we can see the directory name
        val cloneProcess = exec(jpmTmpDirectory.absolutePath, "git clone $path")
        cloneProcess.redirectError(ProcessBuilder.Redirect.INHERIT)
        if (cloneProcess.start().waitFor() != 0) throw CliktError("Could not clone git repository.")
        val directory = jpmTmpDirectory.listFiles().filter { newFiles -> newFiles.name !in currentTmpFiles.map { it.name } }[0]

        if (directory.listFiles().find { it.name == "package-info.json" } == null) {
            val authorRepoNamePair = when {
                path.startsWith("git") -> {
                    val split = path.split(":")
                    split[0] to split[1].removeSuffix(".git")
                }
                else -> {
                    val split = path.split("/")
                    split[split.lastIndex - 1] to split.last().removeSuffix(".git")
                }
            }
            writeObjectToFile(UserPackageInfo(authorRepoNamePair.second, "No description of this package was provided.",
                    authorRepoNamePair.first, "N/A"), directory.absolutePath + "/package-info.json")
        }
        val editExec = exec(directory.absolutePath, "git log -1 --format=\"%at\"").start()
        if (editExec.waitFor() != 0) throw CliktError("Unable to determine last edit time.. is there a commit?")

        val lastEditTime = Scanner(editExec.inputStream).useDelimiter("\\A").next()
                .replace(System.lineSeparator(), "").replace("\n", "").toLong() * 1000

        return (directory.absolutePath) to lastEditTime
    } else {
        // this is local
        val location = File(path)
        if (!location.isDirectory) throw BadParameterValue("Invalid path. Not a directory")
        else if (location.listFiles().isEmpty()) throw BadParameterValue("Invalid location. No files")

        val packageDirectory = File("$jpmTmpDirectory/${location.name}")
        if (packageDirectory.exists()) packageDirectory.deleteRecursively()
        packageDirectory.mkdir()

        FileUtils.copyDirectory(location, packageDirectory)
        if (packageDirectory.listFiles().find { it.name == "package-info.json" } == null) {
            writeObjectToFile(UserPackageInfo(location.name, "No description of this package was provided.",
                    System.getProperty("user.name"), "N/A"), packageDirectory.absolutePath + "/package-info.json")
        }
        val lastEditTime = location.walk().map { it.lastModified() }.sortedByDescending { it }.first()

        return (packageDirectory.absolutePath) to lastEditTime
    }
}

class Install(val jpm: JPM) : CliktCommand("Install a package either locally or via a git url") {
    private val path by argument()
    override fun run() {
        println("=== Unpacking source... ===")
        unpackIntoTmp(path, jpm).let { (directory, lastEditTime) ->
            println("=== Building jar using gradle build.. ===")
            val buildProcess = exec(jpm, "cd $directory && gradle build")
            buildProcess.redirectError(ProcessBuilder.Redirect.INHERIT)
            buildProcess.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            if (buildProcess.start().waitFor() == 0) {
                val source = File(directory)
                val executable = source.listFiles().find { it.name == "build" }?.listFiles()?.find { it.name == "libs" }
                        ?.listFiles()?.find { it.extension.endsWith("jar") }
                        ?: throw CliktError("Build didn't generate a jar")
                val userPackageInfoFile = source.listFiles().find { it.name == "package-info.json" }
                        ?: throw CliktError("package-info.json not found")
                val userPackageInfo = gson.fromJson(userPackageInfoFile.readText(), UserPackageInfo::class.java)
                        ?: throw CliktError("package-info.json unable to be parsed")
                val packageInstallDirectory = "${jpm.jpmPath}/packages/${userPackageInfo.name}".let {
                    if (!File(it).exists()) File(it)
                    else if (File(it).exists()
                            && userPackageInfo.author == gson.fromJson(File("$it/jpm.json")
                                    .readText(), JPackageInfo::class.java).userDefinedInfo.author) {
                        println("Updating package..")
                        File(it)
                    } else if (!File("$it@${userPackageInfo.author}").exists()) File("$it@${userPackageInfo.author}")
                    else throw CliktError("There's already a package by the same name and author installed. Please uninstall that first.")
                }
                packageInstallDirectory.mkdir()
                writeObjectToFile(JPackageInfo(userPackageInfo, System.currentTimeMillis(), lastEditTime, path),
                        packageInstallDirectory.absolutePath + "/jpm.json")

                FileUtils.copyFile(executable, File(packageInstallDirectory.absolutePath + "/" + executable.name))

                println("Removing temporary source files..")
                source.deleteRecursively()

                println("Successfully installed package ${userPackageInfo.name} to ${packageInstallDirectory.absolutePath}")
            }
        }
    }
}

class Uninstall(val jpm: JPM) : CliktCommand("Uninstall a package by its name") {
    private val name by argument()
    override fun run() {
        val packageDirectory = File("${jpm.jpmPath}/packages/$name")
        if (!packageDirectory.exists()) throw CliktError("Package $name isn't installed!")
        else {
            val packageInfo = gson.fromJson(File(packageDirectory.absolutePath + "/jpm.json").readText(), JPackageInfo::class.java)
            if (TermUi.confirm("Are you sure you want to delete " +
                            "${packageInfo.userDefinedInfo.name} - ${packageInfo.userDefinedInfo.author} from ${packageInfo.packageSourcePath}?", default = true) == true) {
                packageDirectory.deleteRecursively()
                println("Deleted package ${packageInfo.userDefinedInfo.name}.")
            }
        }
    }
}
