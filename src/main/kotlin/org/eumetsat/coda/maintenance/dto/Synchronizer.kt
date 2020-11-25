package org.eumetsat.coda.maintenance.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Synchronizer(
        @JsonProperty("Id")
        val id: String,
        @JsonProperty("Label")
        val label: String,
        @JsonDeserialize(using = EpochDeserializer::class) @JsonSerialize(using = EpochSerializer::class) @JsonProperty("CreationDate")
        val creationDate: LocalDateTime,
        @JsonDeserialize(using = EpochDeserializer::class) @JsonSerialize(using = EpochSerializer::class) @JsonProperty("ModificationDate")
        val modificationDate: LocalDateTime,
        @JsonProperty("ServiceUrl")
        val serviceUrl: String,
        @JsonProperty("ServiceLogin")
        val serviceLogin: String,
        @JsonProperty("RemoteIncoming")
        val remoteIncoming: String,
        @JsonProperty("Schedule")
        val schedule: String,
        @JsonProperty("PageSize")
        val pageSize: Int,
        @JsonProperty("CopyProduct")
        val copyProduct: Boolean?,
        @JsonProperty("FilterParam")
        val filterParam: String?,
        @JsonProperty("GeoFilter")
        val geoFilter: String?,
        @JsonProperty("SourceCollection")
        val sourceCollection: String?,
        @JsonDeserialize(using = EpochDeserializer::class) @JsonSerialize(using = EpochSerializer::class) @JsonProperty("LastCreationDate")
        val lastCreationDate: LocalDateTime?,
        @JsonProperty("Status")
        val status: String,
        @JsonDeserialize(using = EpochDeserializer::class) @JsonSerialize(using = EpochSerializer::class) @JsonProperty("StatusDate")
        val statusDate: LocalDateTime?
)