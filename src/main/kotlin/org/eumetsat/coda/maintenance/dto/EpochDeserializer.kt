package org.eumetsat.coda.maintenance.dto

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.time.LocalDateTime
import java.time.ZoneOffset

class EpochDeserializer : JsonDeserializer<LocalDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): LocalDateTime {
        val t = p.valueAsString
        val k = t.substring(6, t.length - 2).toLong()
        return LocalDateTime.ofEpochSecond(k / 1000, (k % 1000 * 1_000_000).toInt(), ZoneOffset.UTC)
    }
}

class EpochSerializer : JsonSerializer<LocalDateTime>() {
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, serializers: SerializerProvider?) {
        gen.writeString("/Date(${value.toEpochSecond(ZoneOffset.UTC)}000)/")
    }
}