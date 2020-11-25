package org.eumetsat.coda.maintenance.dto

//{
//    "id": 7,
//    "url": "file://data-archive-OFL/inbox",
//    "username": "dhus",
//    "status": "running",
//    "statusMessage": "Started on Monday 29 June 2020 - 07:03:01",
//    "pattern": "S3.*",
//    "collections": [],
//    "active": true
//}

data class FileScanner(
        var id: String,
        var url: String,
        var username: String? = null,
        var password: String? = null,
        var status: String?  =null,
        var statusMessage: String? = null,
        var pattern: String,
        var collections: List<String> = mutableListOf(),
        var active: Boolean?
)
