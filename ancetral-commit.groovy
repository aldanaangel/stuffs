pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                // Trae el repo completo
                checkout scm
            }
        }

        stage('Validate QA History') {
            steps {
                script {
                    // Trae todas las ramas del remoto
                    sh 'git fetch --all'

                    // Guarda el último commit de qa
                    def qaCommit = sh(
                        script: "git rev-parse origin/qa",
                        returnStdout: true
                    ).trim()

                    // Guarda el commit actual de la rama (ej: prod)
                    def currentCommit = sh(
                        script: "git rev-parse HEAD",
                        returnStdout: true
                    ).trim()

                    // Compara si QA es ancestro del commit actual
                    def result = sh(
                        script: "git merge-base --is-ancestor ${qaCommit} ${currentCommit}",
                        returnStatus: true
                    )

                    if (result != 0) {
                        error """
                         El commit actual (${currentCommit}) NO contiene los cambios de QA (${qaCommit}).
                        Bloqueando despliegue a prod.
                        """
                    } else {
                        echo " Validación OK: El commit de QA ya está en la historia de Prod."
                    }
                }
            }
        }

        stage('Deploy to Prod') {
            when {
                expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
            }
            steps {
                echo " Desplegando a Producción..."
            }
        }
    }
}
