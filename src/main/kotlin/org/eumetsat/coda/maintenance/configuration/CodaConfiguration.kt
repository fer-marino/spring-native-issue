package org.eumetsat.coda.maintenance.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("coda") @ConstructorBinding
data class CodaConfiguration (val instances: List<CodaInstance>)

@ConstructorBinding
data class CodaInstance (var name: String, var description: String, var url: String, var username: String, var password: String, var count: Long?)
