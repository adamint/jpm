package com.adamratzman.jpm.run

import com.adamratzman.jpm.JPM
import com.adamratzman.jpm.exec
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import java.io.File

class Run(val jpm: JPM) : CliktCommand("Run a package using jpm run PACKAGE ARGS") {
    private val name by argument("Package name")
    private val arguments by argument("Jar flags arguments").multiple(false)
    override fun run() {
        val packageDirectory = File("${jpm.jpmPath}/packages/$name")
        if (!packageDirectory.exists()) throw CliktError("Package $name isn't installed!")
        else {
            val processBuilder = exec(jpm,"java -jar ${packageDirectory.listFiles().first { it.extension.endsWith("jar") }.absolutePath} ${arguments.joinToString(" ")}")
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            processBuilder.start().waitFor()
        }
    }

}