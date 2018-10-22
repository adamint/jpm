package com.adamratzman.jpm

import com.adamratzman.jpm.cache.JPackage
import com.adamratzman.jpm.cache.Package
import com.adamratzman.jpm.cache.Packages
import com.adamratzman.jpm.cache.buildCache
import com.adamratzman.jpm.install.Install
import com.adamratzman.jpm.install.Uninstall
import com.adamratzman.jpm.run.Run
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.google.gson.Gson
import com.sun.javafx.PlatformUtil
import java.io.File



val gson = Gson()

fun main(args: Array<String>) {
    JPM().let { it.subcommands(Install(it), Uninstall(it), Run(it), Version(), Packages(it), Package(it)).main(args) }
}

class JPM : CliktCommand(name = "jpm") {
    override fun run() {
    }

    val jpmPath = File(JPM::class.java.protectionDomain.codeSource.location.toURI()).absolutePath.removeSuffix("jpm.jar") + "jpm-data"
    val cache: List<JPackage> = buildCache(jpmPath)
}

class Version : CliktCommand("JPM version info & credits") {
    private val author = "Adam Ratzman"
    private val version = "0.0.ALPHA-1"
    override fun run() {
        println("JPM Version $version by $author with <3")
    }
}

fun hasGradle(jpm: JPM): Boolean {
    return exec(jpm,"gradle -v").start().waitFor() == 0
}

fun exec(jpm: JPM, command: String) = exec(jpm.jpmPath, command)

fun exec(path:String, command: String): ProcessBuilder = when {
    PlatformUtil.isWindows() -> {
        ProcessBuilder(listOf("cmd", "/c", "cd", path, "&&", "cmd", "/c", command))
    }
    else -> throw NotImplementedError()
}