// vars/requirePromotionTag.groovy
// Verifica que la feature haya pasado por el ambiente previo mediante la existencia de un tag remoto.

def call(String targetEnv, String remote = 'origin', String sourceRef = null) {
    if (!(targetEnv in ['qa', 'prod'])) {
        echo "[requirePromotionTag] No se requiere validación de tag para targetEnv=${targetEnv}"
        return
    }

    sh "git fetch --tags ${remote} +refs/heads/*:refs/remotes/${remote}/*"

    def sourceBranch = sourceRef ?: env.CHANGE_BRANCH
    if (!sourceBranch) {
        error "[requirePromotionTag] No se detectó la rama source (CHANGE_BRANCH)."
    }

    def sourceSha = sh(returnStdout: true, script: "git rev-parse ${remote}/${sourceBranch} | cut -c1-8").trim()
    if (!sourceSha) {
        error "[requirePromotionTag] No se pudo resolver SHA para ${remote}/${sourceBranch}"
    }

    def requiredTag = (targetEnv == 'qa') ? "promote/dev/${sourceSha}" : "promote/qa/${sourceSha}"
    def exists = sh(returnStdout: true, script: "git ls-remote --tags ${remote} ${requiredTag} | wc -l").trim()

    if (exists == '0') {
        error "[requirePromotionTag] ❌ Falta tag requerido '${requiredTag}'. Primero promueve la MISMA feature al ambiente previo."
    }

    echo "[requirePromotionTag] ✅ Tag encontrado: ${requiredTag}"
}
