// === Parámetros de tu repo (ajústalos) ===
def repoUrl        = '<TU_GIT_URL>'          // p.ej. https://bitbucket/.../WAS-DIGITAL.git
def branch         = 'main'
def credentialsId  = 'git-cred-id'           // ID en Jenkins

// Carpeta lógica para agrupar los jobs (opcional)
def folderName = 'WAS-DIGITAL'
folder(folderName) {
  displayName(folderName)
  description('Pipelines generados automáticamente desde files/*.yaml')
}

// Recorre todos los YAML dentro de files/
def filesDir = new File('files')
if (!filesDir.exists()) {
  throw new IllegalStateException("No existe la carpeta 'files' en el workspace del seed.")
}

filesDir.eachFileMatch(~/.*\.ya?ml/) { f ->
  def base = f.name.replaceAll(/\.ya?ml$/, '')

  pipelineJob("${folderName}/${base}") {
    description("Auto-generado por el seed a partir de files/${f.name}")

    // Pasamos la ruta del YAML como parámetro al job
    parameters {
      stringParam('CONFIG_FILE', "files/${f.name}", 'Ruta al YAML dentro del repo')
    }

    // El pipeline de cada job se lee desde el SCM del mismo repo (Jenkinsfile en la raíz)
    definition {
      cpsScm {
        lightweight(true)
        scriptPath('Jenkinsfile') // Jenkinsfile usado por TODOS los jobs
        scm {
          git {
            remote {
              url(repoUrl)
              credentials(credentialsId)
            }
            branch(branch)
          }
        }
      }
    }

    // Opcional: trigger de SCM
    triggers {
      scm('H/5 * * * *')
    }

    // Buenas prácticas
    logRotator {
      numToKeep(20)
      daysToKeep(30)
    }

    // Etiquetas o restricciones de agente si aplica
    // properties { disableConcurrentBuilds() }
  }
}
