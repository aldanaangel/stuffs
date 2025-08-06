import os
import requests
import hashlib

# === CONFIGURACIÓN ===
JENKINS_URL = 'http://localhost:8080'
JENKINS_USER = 'admin'
JENKINS_TOKEN = 'tu-token-aqui'
REPO_PATH = 'DEPLOY-CONFIG'
BASE_CONFIG_XML_PATH = 'base-pipeline.xml'


# === FUNCIONES ===

def load_base_config():
    with open(BASE_CONFIG_XML_PATH, 'r', encoding='utf-8') as f:
        return f.read()


def hash_content(content):
    return hashlib.md5(content.encode('utf-8')).hexdigest()


def jenkins_api_request(method, path, **kwargs):
    url = f"{JENKINS_URL}{path}"
    return requests.request(method, url, auth=(JENKINS_USER, JENKINS_TOKEN), **kwargs)


def folder_exists(folder_path):
    path = f"/job/{'/job/'.join(folder_path.split('/'))}/api/json"
    response = jenkins_api_request("GET", path)
    return response.status_code == 200


def create_folder(folder_path):
    segments = folder_path.split('/')
    for i in range(1, len(segments)+1):
        partial_path = '/'.join(segments[:i])
        if not folder_exists(partial_path):
            parent = '/'.join(segments[:i-1])
            name = segments[i-1]
            path = f"/createItem?name={name}"
            if parent:
                path = f"/job/{'/job/'.join(parent.split('/'))}/createItem?name={name}"
            headers = {'Content-Type': 'application/xml'}
            data = """
<com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder">
  <description></description>
</com.cloudbees.hudson.plugins.folder.Folder>
""".strip()
            response = jenkins_api_request(
                "POST", path, headers=headers, data=data)
            if response.status_code == 200:
                print(f"📁 Carpeta creada: {partial_path}")
            elif response.status_code == 400:
                print(f"⚠️  La carpeta '{partial_path}' ya existe.")
            else:
                print(
                    f"❌ Error creando carpeta '{partial_path}': {response.status_code} - {response.text}")


def job_exists(job_path):
    path = f"/job/{'/job/'.join(job_path.split('/'))}/config.xml"
    response = jenkins_api_request("GET", path)
    return response.status_code == 200


def get_current_config(job_path):
    path = f"/job/{'/job/'.join(job_path.split('/'))}/config.xml"
    response = jenkins_api_request("GET", path)
    return response.text if response.status_code == 200 else None


def create_or_update_job(job_path, base_config):
    folder_path = '/'.join(job_path.split('/')[:-1])
    if folder_path:
        create_folder(folder_path)

    if job_exists(job_path):
        current_config = get_current_config(job_path)
        if current_config:
            if hash_content(current_config) != hash_content(base_config):
                print(
                    f"🔄 Job '{job_path}' existe pero ha cambiado. Actualizando...")
                update_job_config(job_path, base_config)
            else:
                print(f"✅ Job '{job_path}' ya existe y está actualizado.")
        else:
            print(f"⚠️  No se pudo leer el config actual de '{job_path}'")
    else:
        print(f"🆕 Job '{job_path}' no existe. Creando...")
        create_job(job_path, base_config)


def create_job(job_path, config_xml):
    name = job_path.split('/')[-1]
    folder = '/'.join(job_path.split('/')[:-1])
    path = f"/createItem?name={name}" if not folder else f"/job/{'/job/'.join(folder.split('/'))}/createItem?name={name}"
    headers = {'Content-Type': 'application/xml'}
    response = jenkins_api_request(
        "POST", path, headers=headers, data=config_xml.encode('utf-8'))
    if response.status_code == 200:
        print(f"✅ Job creado: {job_path}")
    elif response.status_code == 400:
        print(f"⚠️  El job '{job_path}' ya existe.")
    else:
        print(
            f"❌ Error creando job '{job_path}': {response.status_code} - {response.text}")


def update_job_config(job_path, config_xml):
    path = f"/job/{'/job/'.join(job_path.split('/'))}/config.xml"
    headers = {'Content-Type': 'application/xml'}
    response = jenkins_api_request(
        "POST", path, headers=headers, data=config_xml.encode('utf-8'))
    if response.status_code == 200:
        print(f"🔁 Job '{job_path}' actualizado correctamente.")
    else:
        print(
            f"❌ Error actualizando job '{job_path}': {response.status_code} - {response.text}")


def recorrer_directorio_y_crear_o_actualizar_jobs():
    base_config = load_base_config()
    for root, dirs, _ in os.walk(REPO_PATH):
        for dir_name in dirs:
            full_path = os.path.join(root, dir_name)
            rel_path = os.path.relpath(full_path, REPO_PATH)
            job_path = rel_path.replace(os.sep, '/')
            create_or_update_job(job_path, base_config)


# === EJECUCIÓN ===
if __name__ == '__main__':
    recorrer_directorio_y_crear_o_actualizar_jobs()
