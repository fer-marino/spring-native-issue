package org.eumetsat.coda.maintenance.commands

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.asciithemes.TA_GridThemes
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import org.eumetsat.coda.maintenance.configuration.CodaConfiguration
import org.eumetsat.coda.maintenance.configuration.CodaInstance
import org.eumetsat.coda.maintenance.dto.FileScanner
import org.eumetsat.coda.maintenance.dto.Synchronizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.text.DecimalFormat
import java.util.*


@KtorExperimentalAPI
@Component
@Command(name = "dhus")
class DhUSCommands {
    val log: org.slf4j.Logger = LoggerFactory.getLogger(DhUSCommands::class.java)

    @Autowired
    lateinit var config: CodaConfiguration

    var selected: CodaInstance? = null
    var client: HttpClient? = null

    fun select(id: String) {
        client?.close()
        selected = config.instances.find { it.name == id }

        if (selected == null)
            throw java.lang.IllegalArgumentException("CODA instance $id not found")
        else
            client = HttpClient(CIO) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 5 * 60_000
                }
                install(JsonFeature) {
                    serializer = JacksonSerializer {
                        registerModule(JavaTimeModule())
                    }

                }
                install(Auth) {
                    basic {
                        username = selected!!.username
                        password = selected!!.password
                    }
                }
//                install(Logging) {
//                    logger = Logger.DEFAULT
//                    level = LogLevel.INFO
//                }
            }
    }

    @Command(name = "list-codas")
    fun listCODAs() {
        val at = AsciiTable()

        at.context.setGridTheme(TA_GridThemes.FULL)
        at.context.width = 150
        at.addRule()
        at.addRow("Name", "Description", "URL")
        at.addRule()
        config.instances.forEach {
            at.addRow(it.name, it.description, it.url)
            at.addRule()
        }

        println(at.render())
    }

    @Command(name = "list-synchronizers")
    fun listSynchronizers(@Parameters(description = ["CODA ID"]) codaID: String) {
        select(codaID)

        val synchronizers = (runBlocking {
            client!!.get<Map<String, Map<String, List<Synchronizer>>>>("${selected!!.url}/odata/v1/Synchronizers")
        }["d"] ?: error("Invalid result"))["results"] ?: error("Invalid result")

        val at = AsciiTable()
        at.context.width = 150
        at.addRule()
        at.addRow("ID", "Label", "URL", "Remote Incoming", "CRON pattern", "Page Size", "OData Filter",
                "Last ingested creation date", "Status"
        )
        at.addRule()
//        val model: TableModel = BeanListTableModel<Synchronizer>(synchronizers, header as LinkedHashMap<String, Any>)
        synchronizers.forEach {
            at.addRow(it.id, it.label, it.serviceUrl, it.remoteIncoming, it.schedule, it.pageSize, it.filterParam, it.lastCreationDate, it.status)
            at.addRule()
        }
//        tableBuilder.on(CellMatchers.table()).addSizer(NoWrapSizeConstraints())
//        tableBuilder.addFullBorder(BorderStyle.fancy_light)
//        tableBuilder.addHeaderBorder(BorderStyle.fancy_heavy)
        println(at.render())
    }

    fun startSynchronizer(id: Int) {
        val ris = runBlocking {
            client!!.put<HttpResponse>("${selected!!.url}/odata/v1/Synchronizers($id)") {
                body = mapOf("Id" to id.toString(), "Request" to "start")
                headers {
                    contentType(ContentType.parse("application/json"))
                }
            }
        }

        if (ris.status.value in 200..299)
            log.info("Synchronizer started")
        else
            log.error("Start failed. Status code ${ris.status}. " + ris.headers["cause-message"])
    }

    fun stopSynchronizer(id: Int) {
        val ris = runBlocking {
            client!!.put<HttpResponse>("${selected!!.url}/odata/v1/Synchronizers($id)") {
                body = mapOf("Id" to id.toString(), "Request" to "stop")
                headers {
                    contentType(ContentType.parse("application/json"))
                }
            }
        }

        if (ris.status.value in 200..299)
            log.info("Synchronizer stopped")
        else
            log.error("Start failed. Status code ${ris.status}. " + ris.headers["cause-message"])
    }

    @KtorExperimentalAPI
    @Command(name = "count")
    fun count(@Parameters(description = ["CODA ID"]) id: String) {
        println(
                when {
                    id == "" -> runBlocking {
                        client!!.get<String>("${selected!!.url}/odata/v1/Products/\$count")
                    }
                    config.instances.firstOrNull { it.name == id } != null -> runBlocking {
                        select(id)
                        client!!.get<String>("${config.instances.first { it.name == id }.url}/odata/v1/Products/\$count") {
                            timeout {
                                requestTimeoutMillis = 5*60000
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Unknown CODA id $id")
                }
        )
    }

    fun getSynchronizer(id: Int): Synchronizer = runBlocking {
        client!!.get<Map<String, Synchronizer>>("${selected!!.url}/odata/v1/Synchronizers($id)")
    }["d"] ?: error("Synchronizer not found")

    @Command(name = "list-filescanners")
    fun listFileScanners(@Parameters(description = ["CODA ID"]) codaID: String) {
        select(codaID)

        val fileScanners = runBlocking {
            client!!.get<List<FileScanner>>("${selected!!.url}/api/stub/admin/filescanners")
        }

        val at = AsciiTable()
        at.context.width = 150
        at.addRule()
        at.addRow("ID", "URL", "Username", "Status", "Status Message", "Filename pattern", "Collections", "Active")
        at.addRule()
//        val model: TableModel = BeanListTableModel<FileScanner>(fileScanners, header as LinkedHashMap<String, Any>)
//        val tableBuilder = TableBuilder(model)
//        tableBuilder.on(CellMatchers.table()).addSizer(NoWrapSizeConstraints())
//        tableBuilder.addFullBorder(BorderStyle.fancy_light)
//        tableBuilder.addHeaderBorder(BorderStyle.fancy_heavy)
        fileScanners.forEach {
            at.addRow(it.id, it.url, it.username, it.status, it.statusMessage, it.pattern, it.collections, it.active)
            at.addRule()
        }
        println(at.render())
    }

    fun createFileScanner(id: String, url: String, pattern: String, activate: Boolean) {
        val ris = runBlocking {
            client!!.post<HttpResponse>("${selected!!.url}/api/stub/admin/filescanners") {
                body = FileScanner(id = id, url = url, pattern = pattern, active = activate)
            }
        }

        if (ris.status.value in 200..299)
            log.info("File scanner $id created")
        else
            log.error("Creation failed. Status code ${ris.status}. " + ris.headers["cause-message"])
    }

    fun deleteFileScanner(id: String) {
        val ris = runBlocking {
            client!!.delete<HttpResponse>("${selected!!.url}/api/stub/admin/filescanners/$id")
        }

        if (ris.status.value in 200..299)
            log.info("File scanner $id deleted")
        else
            log.error("Delete failed. Status code ${ris.status}. " + ris.headers["cause-message"])
    }

    fun updateFileScanner(id: String, url: String, pattern: String, start: Boolean, stop: Boolean, activate: Boolean) {
        val ris = runBlocking {
            client!!.put<HttpResponse>("${selected!!.url}/api/stub/admin/filescanners/$id") {
                body = FileScanner(id = id, url = url, pattern = pattern, active = activate)
                parameter("start", start)
                parameter("stop", stop)
                parameter("activate", activate)
            }
        }

        if (ris.status.value in 200..299)
            log.info("File scanner $id updated")
        else
            log.error("Update failed. Status code ${ris.status}. " + ris.headers["cause-message"])
    }


}

