#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://bitbucket.miempresa.com"
AUTH="usuario:token"
INPUT_FILE="bitbucket_inventory.csv"
OUTPUT_FILE="bitbucket_project_webhooks.csv"
LIMIT=100

echo '"scope","project_key","webhook_id","webhook_name","active","url","events","configuration","webhook_json"' > "$OUTPUT_FILE"

cut -d',' -f1 "$INPUT_FILE" \
| tail -n +2 \
| tr -d '"' \
| sort -u \
| while read -r project_key; do

  echo "Consultando webhooks de proyecto: $project_key"

  start=0

  while true; do
    response=$(curl -s -u "$AUTH" \
      "$BASE_URL/rest/api/latest/projects/$project_key/webhooks?limit=$LIMIT&start=$start")

    size=$(echo "$response" | jq -r '.size // 0')
    is_last=$(echo "$response" | jq -r '.isLastPage // true')
    next_start=$(echo "$response" | jq -r '.nextPageStart // empty')

    echo "  Página start=$start -> webhooks encontrados: $size"

    echo "$response" | jq -c '.values[]?' | while read -r webhook; do
      jq -n -r \
        --arg scope "PROJECT" \
        --arg project_key "$project_key" \
        --arg webhook_id "$(echo "$webhook" | jq -r '.id // ""')" \
        --arg webhook_name "$(echo "$webhook" | jq -r '.name // ""')" \
        --arg active "$(echo "$webhook" | jq -r '.active // ""')" \
        --arg url "$(echo "$webhook" | jq -r '.url // ""')" \
        --arg events "$(echo "$webhook" | jq -r '(.events // []) | join(";")')" \
        --arg configuration "$(echo "$webhook" | jq -c '.configuration // {}')" \
        --arg webhook_json "$(echo "$webhook" | jq -c '.')" \
        '[
          $scope,
          $project_key,
          $webhook_id,
          $webhook_name,
          $active,
          $url,
          $events,
          $configuration,
          $webhook_json
        ] | @csv' >> "$OUTPUT_FILE"
    done

    [[ "$is_last" == "true" ]] && break
    start="$next_start"
  done

done

echo "Archivo generado: $OUTPUT_FILE"
