pipeline {
    agent any
    environment {
        GIT_REPO = 'git@bitbucket.org:empresa/jenkins-config.git'
        BRANCH = 'main'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: "${BRANCH}", url: "${GIT_REPO}"
            }
        }

        stage('Crear estructura de jobs') {
            steps {
                jobDsl targets: 'dsl/create_jobs.groovy',
                       removedJobAction: 'IGNORE',
                       removedViewAction: 'IGNORE'
            }
        }

        stage('Detectar cambios en config.yaml') {
            steps {
                script {
                    def cambios = sh(
                        script: 'git diff --name-only HEAD~1 HEAD | grep config.yaml || true',
                        returnStdout: true
                    ).trim().split('\n')

                    if (cambios.size() == 0 || cambios[0] == '') {
                        echo '✅ No hay cambios en archivos config.yaml. Nada que actualizar.'
                    } else {
                        def archivos = cambios.collect { "\"${it}\"" }.join(' ')
                        echo "🔧 Archivos modificados: ${archivos}"
                        sh "python3 scripts/update_config_if_changed.py ${archivos}"
                    }
                }
            }
        }
    }
}
