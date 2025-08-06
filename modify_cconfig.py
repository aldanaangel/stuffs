import os
import sys
import requests
import yaml

JENKINS_URL = "http://TU_JENKINS"
JENKINS_USER = "usuario"
JENKINS_TOKEN = "token"
TEMPLATE_PATH = "templates/config_template.xml"
BASE_PATH = "DEPLOY-CONFIG"


def job_exists(job_path_list):
    job_path_str = "/job/" + "/job/".join(job_path_list)
    url = f"{JENKINS_URL}{job_path_str}/api/json"
    response = requests.get(url, auth=(JENKINS_USER, JENKINS_TOKEN))
    return response.status_code == 200


def cargar_variables_yaml(path_yaml):
    with open(path_yaml, "r") as f:
        return yaml.safe_load(f) or {}


def update_config(job_path_list, variables):
    with open(TEMPLATE_PATH, "r") as f:
        xml_template = f.read()

    for k, v in variables.items():
        xml_template = xml_template.replace(f"{{{{{k}}}}}", str(v))

    job_path_str = "/job/" + "/job/".join(job_path_list)
    url = f"{JENKINS_URL}{job_path_str}/config.xml"
    headers = {'Content-Type': 'application/xml'}
    response = requests.post(
        url, data=xml_template, headers=headers, auth=(JENKINS_USER, JENKINS_TOKEN))

    if response.status_code == 200:
        print(f"✅ Actualizado: {job_path_str}")
    else:
        print(f"❌ Error ({response.status_code}) al actualizar {job_path_str}")


def main():
    yaml_files = sys.argv[1:]
    if not yaml_files:
        print("⚠️ No se recibieron archivos como parámetro.")
        return

    for yaml_path in yaml_files:
        full_path = os.path.abspath(yaml_path)
        rel_path = os.path.relpath(full_path, BASE_PATH)
        job_path = os.path.dirname(rel_path).split(os.sep)

        if job_exists(job_path):
            variables = cargar_variables_yaml(full_path)
            update_config(job_path, variables)
        else:
            print(f"⚠️ El job no existe aún en Jenkins: {'/'.join(job_path)}")


if __name__ == "__main__":
    main()
