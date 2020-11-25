package org.eumetsat.coda.maintenance.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class Entry(val id: String, val title: String, val updated: LocalDateTime = LocalDateTime.now(), val category: String, val content: Any)