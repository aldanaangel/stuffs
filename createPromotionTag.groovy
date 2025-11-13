// vars/createPromotionTag.groovy
// Crea y pushea un tag de promoción (promote/<env>/<sha>) tras un merge exitoso.

def call(String envName, String version = null, String remote = 'origin',
         String credentialsId = 'git-jenkins',
         String gitUserName = 'Jenkins CI',
         String gitUserEmail = 'jenkins@company.local') {

    if (!envName?.trim()) error "[createPromotionTag] envName requerido"

    sh "git fetch --tags ${remote}"

    // Determina el SHA de la feature mergeada (HEAD^2)
    def parents = sh(returnStdout: true, script: "git rev-list --parents -n1 HEAD | awk '{print NF-1}'").trim()
    if (parents != '2') {
        echo "[createPromotionTag] ⚠️  HEAD no parece un merge commit (parents=${parents}). ¿Tienes habilitado 'merge commit'?"
    }
    def shortSha = sh(returnStdout: true, script: "git rev-parse HEAD^2 | cut -c1-8").trim()

    if (!shortSha) {
        error "[createPromotionTag] No se pudo resolver HEAD^2. Asegura estrategia 'merge commit'."
    }

    def tag = "promote/${envName}/${shortSha}"

    // Verifica si ya existe
    def exists = sh(returnStdout: true, script: "git ls-remote --tags ${remote} ${tag} | wc -l").trim()
    if (exists == '1') {
        echo "[createPromotionTag] Tag ya existe en remoto: ${tag}"
        return
    }

    def msg = "Promoted ${shortSha} on ${envName}" + (version ? " (version ${version})" : "")
    sh """
        git config user.name  "${gitUserName}"
        git config user.email "${gitUserEmail}"
        git tag -a '${tag}' -m '${msg}'
    """

    sshagent(credentials: [credentialsId]) {
        sh "git push ${remote} '${tag}'"
    }

    echo "[createPromotionTag] ✅ Tag creado y enviado: ${tag}"
}
