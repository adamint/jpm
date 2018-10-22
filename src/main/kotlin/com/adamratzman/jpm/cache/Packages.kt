package com.adamratzman.jpm.cache

import com.adamratzman.jpm.JPM
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.arguments.argument
import java.text.DateFormat
import java.time.Instant
import java.util.*

class Packages(val jpm: JPM) : CliktCommand("List all packages. Search for a package using jpm search package or get info on a specific package with jpm package package-name") {
    override fun run() {
        val sb = StringBuilder("JPM Package List >")
        if (jpm.cache.isEmpty()) sb.append(" No packages installed! Install using jpm install")
        else {
            jpm.cache.forEach { jpmPackage ->
                sb.append("\n  - ${jpmPackage.jPackageInfo.userDefinedInfo}")
                if (jpmPackage.directoryName != jpmPackage.jPackageInfo.userDefinedInfo.name) {
                    sb.append(" - installed to name: ${jpmPackage.directoryName}")
                }
            }
            sb.append("\n\nListed all packages. Search for a package using jpm search package or get info on a specific package with jpm package package-name")
        }
        println (sb)
    }
}

class Package(val jpm: JPM) : CliktCommand("Get info on a specific package") {
    private val name by argument("Package name")
    override fun run() {
        val found = jpm.cache.filter { it.jPackageInfo.userDefinedInfo.name == name }
        when {
            found.isEmpty() -> println("No package by that name was found")
            found.size > 1 -> {
                val packages = found.joinToString("\n") { it.directoryName + " > ${it.jPackageInfo.userDefinedInfo.author}" }
                println("Found packages:\n$packages")
                val jPackage = TermUi.prompt("Multiple packages by that name were found. Which author do you want to choose?")?.let { chosen ->
                    found.find { it.jPackageInfo.userDefinedInfo.author.equals(chosen, true) }
                } ?: throw CliktError("No author by that name was found. Please try again")
                display(jPackage)
            }
            else -> display(found[0])
        }
    }

    private fun display(jPackage: JPackage) {
        println("Package ${jPackage.jPackageInfo.userDefinedInfo}")
        println("Description: ${jPackage.jPackageInfo.userDefinedInfo.description}")
        println("Installed at ${jPackage.jPackageInfo.packageInstallDate.getPackageDate()}")
        println("Package originally taken from ${jPackage.jPackageInfo.packageSourcePath} - last edited at ${jPackage.jPackageInfo.sourceLastEditDate.getPackageDate()}")
    }
}

fun Long.getPackageDate() = DateFormat.getInstance().format(Date.from(Instant.ofEpochMilli(this)))!!