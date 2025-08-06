import java.util.regex.Pattern

def basePath = 'DEPLOY-CONFIG'
def rootDir = new File(basePath)

if (!rootDir.exists() || !rootDir.isDirectory()) {
    println "⚠️ El directorio '${basePath}' no existe. Asegúrate de ejecutar este script desde el workspace correcto."
    return
}

rootDir.eachFileRecurse { file ->
    if (file.isDirectory()) {
        def relPath = file.path.replace("${basePath}${File.separator}", '')
        def pathParts = relPath.split(Pattern.quote(File.separator))

        if (pathParts.size() > 0) {
            def jobName = pathParts[-1]                     // dev, qa, prd, etc.
            def folderPath = pathParts[0..-2].join('/')     // jerarquía de carpetas sin el job

            // Crear jerarquía de carpetas
            folderPath.split('/').inject('') { acc, part ->
                def fullPath = acc ? "${acc}/${part}" : part
                folder(fullPath)
                return fullPath
            }

            // Crear pipeline con lógica por entorno
            pipelineJob("${folderPath}/${jobName}") {
                description("Pipeline para entorno: ${jobName}")
                definition {
                    cps {
                        script("""
                            pipeline {
                                agent any
                                environment {
                                    ENV = "${jobName}"
                                }
                                stages {
                                    stage('Mostrar entorno') {
                                        steps {
                                            echo "Este pipeline es del entorno: ${jobName}"
                                        }
                                    }
                                }
                            }
                        """.stripIndent())
                        sandbox(true)
                    }
                }
            }
        }
    }
}
