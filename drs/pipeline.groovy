// 🔸 Leer namespaces.json desde el repositorio (mismo nivel que Jenkinsfile)
def jsonText = readFile 'namespaces.json'
def namespaces = new groovy.json.JsonSlurperClassic().parseText(jsonText)

def prodChoices = namespaces.prod ?: []
def drsChoices  = namespaces.drs  ?: []

// 🔸 Definir parámetros dinámicos desde el archivo JSON
properties([
    parameters([
        choice(name: 'NAMESPACE_PROD', choices: prodChoices.join('\n'), description: 'Namespace de Producción'),
        choice(name: 'NAMESPACE_DRS',  choices: drsChoices.join('\n'),  description: 'Namespace de DRS (respaldo)'),
        string(name: 'MICROSERVICE_NAME', defaultValue: '', description: 'Nombre del microservicio (Deployment)'),
        choice(name: 'CHECK', choices: ['DRS', 'PROD'], description: 'Dirección del escalado')
    ])
])

pipeline {
    agent any

    environment {
        OC = 'oc'
        API_URL_PROD = 'https://api.cluster-prod.example.com:6443'
        API_URL_DRS  = 'https://api.cluster-drs.example.com:6443'
    }

    stages {
        stage('Validar parámetros') {
            steps {
                script {
                    if (!params.NAMESPACE_PROD?.trim()) error '❌ Falta NAMESPACE_PROD'
                    if (!params.NAMESPACE_DRS?.trim()) error '❌ Falta NAMESPACE_DRS'
                    if (!params.MICROSERVICE_NAME?.trim()) error '❌ Falta MICROSERVICE_NAME'
                    echo '✅ Parámetros validados correctamente'
                }
            }
        }

        stage('Cargar tokens desde tokens.json') {
            steps {
                script {
                    def tokens = readJSON file: 'tokens.json'

                    def tokenProd = tokens[params.NAMESPACE_PROD]
                    def tokenDrs  = tokens[params.NAMESPACE_DRS]

                    if (!tokenProd || !tokenDrs) {
                        error '❌ No se encontraron tokens para los namespaces ingresados.'
                    }

                    env.TOKEN_PROD = tokenProd
                    env.TOKEN_DRS  = tokenDrs

                    echo '🔐 Tokens cargados dinámicamente (no se mostrarán por seguridad)'
                }
            }
        }

        stage('Verificar existencia del microservicio en ambos entornos') {
            steps {
                script {
                    def prodExists = false
                    def drsExists  = false

                    sh "${env.OC} login --token=${env.TOKEN_PROD} --server=${env.API_URL_PROD} --insecure-skip-tls-verify"
                    prodExists = sh(script: "${env.OC} get deployment ${params.MICROSERVICE_NAME} -n ${params.NAMESPACE_PROD}", returnStatus: true) == 0

                    sh "${env.OC} login --token=${env.TOKEN_DRS} --server=${env.API_URL_DRS} --insecure-skip-tls-verify"
                    drsExists = sh(script: "${env.OC} get deployment ${params.MICROSERVICE_NAME} -n ${params.NAMESPACE_DRS}", returnStatus: true) == 0

                    if (!prodExists || !drsExists) {
                        error "❌ El microservicio no existe en ambos entornos. [PROD=${prodExists}, DRS=${drsExists}]"
                    }

                    echo '✅ El microservicio existe en ambos namespaces.'
                }
            }
        }

        stage('Escalar entorno CHECK a 1') {
            steps {
                script {
                    def ns     = (params.CHECK == 'DRS') ? params.NAMESPACE_DRS : params.NAMESPACE_PROD
                    def apiUrl = (params.CHECK == 'DRS') ? env.API_URL_DRS       : env.API_URL_PROD
                    def token  = (params.CHECK == 'DRS') ? env.TOKEN_DRS         : env.TOKEN_PROD

                    try {
                        sh "${env.OC} login --token=${token} --server=${apiUrl} --insecure-skip-tls-verify"
                        sh "${env.OC} scale deployment/${params.MICROSERVICE_NAME} --replicas=1 -n ${ns}"
                        sh "${env.OC} wait --for=condition=available deployment/${params.MICROSERVICE_NAME} --timeout=90s -n ${ns}"
                        echo "✅ Escalado exitoso en '${ns}'"
                    } catch (err) {
                        currentBuild.result = 'FAILURE'
                        throw new Exception("❌ Fallo al escalar '${ns}' o sus pods no llegaron a 'Ready'")
                    }
                }
            }
        }

        stage('Escalar entorno contrario a 0') {
            when {
                expression { currentBuild.result == null || currentBuild.result == 'SUCCESS' }
            }
            steps {
                script {
                    def ns     = (params.CHECK == 'DRS') ? params.NAMESPACE_PROD : params.NAMESPACE_DRS
                    def apiUrl = (params.CHECK == 'DRS') ? env.API_URL_PROD       : env.API_URL_DRS
                    def token  = (params.CHECK == 'DRS') ? env.TOKEN_PROD         : env.TOKEN_DRS

                    sh "${env.OC} login --token=${token} --server=${apiUrl} --insecure-skip-tls-verify"
                    sh "${env.OC} scale deployment/${params.MICROSERVICE_NAME} --replicas=0 -n ${ns}"

                    echo "✅ Escalado a 0 en '${ns}'"
                }
            }
        }
    }

    post {
        failure {
            script {
                def failedNS      = (params.CHECK == 'DRS') ? params.NAMESPACE_DRS  : params.NAMESPACE_PROD
                def rollbackNS    = (params.CHECK == 'DRS') ? params.NAMESPACE_PROD : params.NAMESPACE_DRS
                def failedToken   = (params.CHECK == 'DRS') ? env.TOKEN_DRS         : env.TOKEN_PROD
                def rollbackToken = (params.CHECK == 'DRS') ? env.TOKEN_PROD        : env.TOKEN_DRS
                def failedAPI     = (params.CHECK == 'DRS') ? env.API_URL_DRS       : env.API_URL_PROD
                def rollbackAPI   = (params.CHECK == 'DRS') ? env.API_URL_PROD      : env.API_URL_DRS

                echo '🚨 Ejecutando rollback completo...'

                sh "${env.OC} login --token=${failedToken} --server=${failedAPI} --insecure-skip-tls-verify"
                sh "${env.OC} scale deployment/${params.MICROSERVICE_NAME} --replicas=0 -n ${failedNS}"

                sh "${env.OC} login --token=${rollbackToken} --server=${rollbackAPI} --insecure-skip-tls-verify"
                sh "${env.OC} scale deployment/${params.MICROSERVICE_NAME} --replicas=1 -n ${rollbackNS}"
                sh "${env.OC} wait --for=condition=available deployment/${params.MICROSERVICE_NAME} --timeout=90s -n ${rollbackNS}"

                echo "✅ Rollback completado. '${rollbackNS}' está activo nuevamente."
            }
        }
        success {
            echo "🎯 Escalado exitoso según CHECK = '${params.CHECK}'"
        }
    }
}
