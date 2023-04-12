package org.vechain.indexer.utils

import com.google.gson.GsonBuilder

object JSON {

    private val GSON = GsonBuilder().create()

    fun <T> parse(json: String, type: Class<T>): T {
        return GSON.fromJson(json, type)
    }

    fun stringify(obj: Any): String {
        return GSON.toJson(obj)
    }
}
