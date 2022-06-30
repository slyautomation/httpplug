import ProjectVersions.openosrsVersion


version = "1.0.3"

project.extra["PluginName"] = "HTTP Server Sly"
project.extra["PluginDescription"] = "Exposes an HTTP API on localhost:8080 for querying stats and events"

tasks {
	jar {
		manifest {
			attributes(mapOf(
					"Plugin-Version" to project.version,
					"Plugin-Id" to nameToId(project.extra["PluginName"] as String),
					"Plugin-Provider" to project.extra["PluginProvider"],
					"Plugin-Description" to project.extra["PluginDescription"],
					"Plugin-License" to project.extra["PluginLicense"]
			))
		}
	}
}