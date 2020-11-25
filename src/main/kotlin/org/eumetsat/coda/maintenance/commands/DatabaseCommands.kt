package org.eumetsat.coda.maintenance.commands

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import picocli.CommandLine.*
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.ResultSet
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@Component
@Command(name = "DB")
class DatabaseCommands {
    val log = LoggerFactory.getLogger(this::class.java)
    var ds: HikariDataSource? = null
    var tpl: JdbcTemplate? = null

    fun closeDs() {
        if (ds != null && !ds!!.isClosed) {
            log.info("Closing connection")
            ds!!.close()
        }
    }

    fun openDB(path: String) {
        val ds = HikariDataSource().apply {
            jdbcUrl = "jdbc:hsqldb:file:$path"
            username = "sa"
            password = ""
            driverClassName = "org.hsqldb.jdbc.JDBCDriver"
        }

        tpl = JdbcTemplate(ds)

        tpl!!.queryForObject("SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", Integer::class.java)
        log.info("Datbase opened")
    }


    fun deleteProduct(id: String) {
        if (tpl == null) {
            log.info("No database selected")
            return
        }

        log.info("delete product $id")
        try {
            tpl!!.update("DELETE FROM CHECKSUMS WHERE product_id = ?", id)
            tpl!!.update("DELETE FROM PRODUCTS_USER_AUTH WHERE product_id = ?", id)
            tpl!!.update("DELETE FROM products WHERE id = ?", id)
        } catch (e: DataAccessException) {
            e.printStackTrace()
        }
    }

    @ExperimentalTime
    @Command(name = "shutdown-compact")
    fun shutdownCompact(@Parameters(description = ["path of the database to repair"]) path: String) {
        openDB(path)

        val time = measureTimeMillis {
            log.info("Executing shutdown compact")
            tpl!!.update("SHUTDOWN COMPACT")
        }

        log.info("competed in ${time.toDuration(TimeUnit.MILLISECONDS)}")

        if (!ds!!.isClosed)
            ds!!.close()
        ds = null
        tpl = null

        closeDs()
    }


    @ExperimentalTime
    @Command(name = "repair")
    fun repair(
            @Option(names = ["-p", "--prune-deleted-products"], required = false, description = ["Delete from DB products with corrupted metadata"])
            pruneDeletedProducts: Boolean = false,
            @Option(names = ["-m", "--rebuild-metadata-indexes"], required = false, description = ["Rebuild metadata_indexes table"])
            rebuildMetadataIndex: Boolean = true,
            @Option(names = ["-k", "--rebuild-keystore"], required = false, description = ["Rebuild keystore_entries table"])
            rebuildKeystore: Boolean = false,
            @Parameters(description = ["path of the database to repair"]) path: String
    ) {
        openDB(path)

        var last = ""
        var count = 0
        var start = System.currentTimeMillis()
        val toDelete = mutableListOf<String>()
        log.info("Creating new table")
        tpl!!.update("CREATE CACHED TABLE IF NOT EXISTS PUBLIC.METADATA_INDEXES_NEW (PRODUCT_ID BIGINT NOT NULL,CATEGORY VARCHAR(255),NAME VARCHAR(255) NOT NULL,QUERYABLE VARCHAR(255),TYPE VARCHAR(255),\"VALUE\" VARCHAR(8192) NOT NULL,PRIMARY KEY(PRODUCT_ID,NAME,\"VALUE\"))")
        tpl!!.update("CREATE CACHED TABLE IF NOT EXISTS PUBLIC.KEYSTOREENTRIES_NEW (KEYSTORE VARCHAR(255) NOT NULL, ENTRYKEY VARCHAR(255) NOT NULL, VALUE VARCHAR(1024), INSERTIONDATE BIGINT DEFAULT 0 NOT NULL, PRIMARY KEY (KEYSTORE, ENTRYKEY))")
        val total = tpl!!.queryForObject("SELECT count(*) FROM products", Long::class.java)!!.toDouble()

        log.info("fetching product list...")
        tpl!!.query("SELECT id, download_path, identifier, uuid FROM products ORDER BY id") { rs: ResultSet ->
            if (pruneDeletedProducts && Files.notExists(Paths.get(rs.getString("download_path")))) {
                log.info("Pruning product with id ${rs.getString("id")}")
                deleteProduct(rs.getString("id"))
            } else
                try {
                    if (rebuildMetadataIndex && tpl!!.queryForObject("SELECT count(*) FROM metadata_indexes_new WHERE product_id = ?",
                                    Long::class.java, rs.getString("id")) == 0L)
                        tpl!!.query("SELECT PRODUCT_ID, CATEGORY , NAME, QUERYABLE, TYPE, \"VALUE\" FROM metadata_indexes WHERE product_id = ?",
                                arrayOf(rs.getString("id"))) { meta: ResultSet ->
                            tpl!!.update("INSERT INTO metadata_indexes_new (PRODUCT_ID, CATEGORY , NAME, QUERYABLE, TYPE, \"VALUE\") " +
                                    "VALUES (?, ?, ?, ?, ?, ?)", meta.getString("PRODUCT_ID"),
                                    meta.getString("CATEGORY"), meta.getString("NAME"), meta.getString("QUERYABLE"),
                                    meta.getString("TYPE"), meta.getString("VALUE"))
                        }
                    if (rebuildKeystore && tpl!!.queryForObject("SELECT count(*) FROM KEYSTOREENTRIES_NEW WHERE ENTRYKEY = ?",
                                    Long::class.java, rs.getString("uuid")) == 0L) {
                        tpl!!.query("SELECT KEYSTORE, ENTRYKEY, \"VALUE\", INSERTIONDATE FROM KEYSTOREENTRIES WHERE keystore = 'OldIncomingAdapter' AND ENTRYKEY = ?",
                                arrayOf(rs.getString("uuid"))) { meta: ResultSet ->
                            tpl!!.update("INSERT INTO KEYSTOREENTRIES_NEW (KEYSTORE, ENTRYKEY, VALUE, INSERTIONDATE) " +
                                    "VALUES (?, ?, ?, ?)", meta.getString("PRODUCT_ID"),
                                    meta.getString("KEYSTORE"), meta.getString("ENTRYKEY"), meta.getString("VALUE"),
                                    meta.getString("INSERTIONDATE"))
                        }
                    }
                } catch (e: Exception) {
                    log.error("Failed id ${rs.getString("id")} ${rs.getString("identifier")}")
                    toDelete.add(rs.getString("id"))
                }
            count++
            last = rs.getString("id")

            if (System.currentTimeMillis() - start > 30000) {
                log.info("processed $count (%.2f %%)".format(count / total * 100))
                start = System.currentTimeMillis()
            }

        }

        log.info("drop old metadata table")
        tpl!!.update("DROP TABLE metadata_indexes")
        log.info("rename new metadata table")
        tpl!!.update("ALTER TABLE metadata_indexes_new RENAME TO metadata_indexes")
        toDelete.forEach { id -> deleteProduct(id) }
        closeDs()

        shutdownCompact(path)

        log.info("processed $count - lastID $last")

    }
}
