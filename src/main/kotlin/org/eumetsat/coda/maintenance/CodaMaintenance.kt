package org.eumetsat.coda.maintenance

import org.eumetsat.coda.maintenance.commands.DatabaseCommands
import org.eumetsat.coda.maintenance.commands.DhUSCommands
import org.eumetsat.coda.maintenance.configuration.CodaConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import picocli.CommandLine

@SpringBootApplication
@EnableConfigurationProperties(CodaConfiguration::class)
@CommandLine.Command(name = "maintenance")
class CodaMaintenance: CommandLineRunner, ExitCodeGenerator {
    private var exitCode = 0
    @Autowired
    lateinit var dbCommands: DatabaseCommands
    @Autowired
    lateinit var dhusCommands: DhUSCommands
    @Autowired
    lateinit var iFactory: CommandLine.IFactory

    override fun run(vararg args: String?) {
        exitCode = CommandLine(this, iFactory)
                .addSubcommand("DB", dbCommands)
                .addSubcommand("dhus", dhusCommands)
                .execute(*args)
    }

    override fun getExitCode() = exitCode
}

fun main(args: Array<String>) {
    runApplication<CodaMaintenance>(*args)
}
