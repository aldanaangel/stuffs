import json
import csv

archivo_json = 'datos.json'
archivo_csv = 'resultado.csv'

campos = ['nombre', 'correo']

with open(archivo_json, 'r', encoding='utf-8') as f:
    datos = json.load(f)

with open(archivo_csv, 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=campos)
    writer.writeheader()
    for item in datos:
        writer.writerow({campo: item.get(campo, '') for campo in campos})

print(f"Archivo '{archivo_csv}' generado con éxito.")
