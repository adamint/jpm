package com.adamratzman.jpm.utils

import com.adamratzman.jpm.gson
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.Charset

fun writeObjectToFile(obj: Any, path: String) {
    FileUtils.writeStringToFile(File(path), gson.toJson(obj), Charset.defaultCharset())
}