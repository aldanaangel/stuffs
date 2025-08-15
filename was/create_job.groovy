// ===== Ajusta a tu entorno =====
def folderName    = 'WAS-DIGITAL'                // Carpeta raíz para agrupar los jobs
def sharedLibName = 'jenkins-shared-library'     // Global Shared Library registrada en Jenkins
def pipelineClass = 'com.cardif.sharedservice.pipelines.java.was.WasPipeline'
// =================================

// (Opcional) Si quieres restringir qué nombres de ambiente son válidos, define un regex:
 def ENV_ALLOWLIST = ~/^(dev|qa|prd)$/
// Si no lo defines, cualquier clave con { pipelineName: ... } se considera ambiente.

folder(folderName) {
    displayName(folderName)
    description('Pipelines auto-generados desde pipelines/*.yaml')
}

// Utilidad para escapar comillas y backslashes
String esc(String s) { (s ?: '').replace('\\', '\\\\').replace("'", "\\'") }

// Crea un Pipeline Job con script embebido y nombre EXACTO = pipelineName
def createEnvJob = { Map cfg, String envName, Map envCfg, String yamlName ->
    if (!envCfg?.pipelineName) {
        println "Saltando ${yamlName} (${envName}): falta pipelineName"
        return
    }

    // (Opcional) valida allowlist de ambientes
    if (binding.hasVariable('ENV_ALLOWLIST') && !(envName ==~ ENV_ALLOWLIST)) {
        println "Saltando ${yamlName} (${envName}): ambiente no permitido por ENV_ALLOWLIST"
        return
    }

    def jobName = envCfg.pipelineName  // usar exactamente como viene

    def scriptText = """\
@Library('${sharedLibName}') _
import ${pipelineClass}
def pipeline = new WasPipeline()
pipeline.execute(
  applicationName: '${esc(cfg.applicationName)}',
  applicationRepository: '${esc(cfg.applicationRepository ?: '')}',
  environment: '${esc(envName)}'
)
"""

    pipelineJob("${folderName}/${jobName}") {
        description("Generado desde ${yamlName} (env=${envName}).")
        definition {
            cps {
                sandbox(true)   // mantiene sandbox para la Shared Library
                script(scriptText)
            }
        }
        logRotator { daysToKeep(30); numToKeep(40) }
        properties { disableConcurrentBuilds() }
    }
}

// --- Seed principal ---
def yaml = new groovy.yaml.YamlSlurper()
def dir  = new File('pipelines')
if (!dir.exists()) throw new IllegalStateException("No existe la carpeta 'pipelines' en el workspace del seed.")

// Claves "top" que NO son ambientes
def TOP_KEYS = ['applicationName', 'applicationRepository'] as Set

dir.eachFileMatch(~/.*\.ya?ml/) { f ->
    def cfg = yaml.parse(f) as Map
    if (!cfg?.applicationName) {
        println "Saltando ${f.name}: falta 'applicationName'"
        return
    }

    // Detecta como ambientes todas las claves cuyo valor sea Map y tenga pipelineName
    def envKeys = (cfg.keySet() - TOP_KEYS).findAll { k ->
        def v = cfg[k]
        (v instanceof Map) && v.containsKey('pipelineName')
  }.sort { it.toString() } // orden estable

    if (envKeys.isEmpty()) {
        println "No se detectaron ambientes en ${f.name} (no hay bloques con pipelineName)."
    }

    envKeys.each { envName ->
        createEnvJob(cfg, envName as String, cfg[envName] as Map, f.name)
    }
    }
