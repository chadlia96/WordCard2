package com.newpos.wordcard.data

import androidx.room.TypeConverter
import org.json.JSONArray

/** 三个例句列表分别存成 JSON 字符串，读取时恢复为字符串列表。 */
class CardContentConverters {
    @TypeConverter
    fun encodeStrings(values: List<String>): String = JSONArray(values).toString()

    @TypeConverter
    fun decodeStrings(json: String): List<String> = JSONArray(json).let { array ->
        (0 until array.length()).map { array.getString(it) }
    }
}
