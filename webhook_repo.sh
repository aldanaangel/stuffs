#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://bitbucket.miempresa.com"
AUTH="usuario:token"
INPUT_FILE="bitbucket_inventory.csv"
OUTPUT_FILE="bitbucket_repo_webhooks.csv"
LIMIT=100

echo '"scope","project_key","repo_slug","webhook_id","webhook_name","active","url","events","configuration","webhook_json"' > "$OUTPUT_FILE"

total_repos=$(tail -n +2 "$INPUT_FILE" | wc -l | tr -d ' ')
current_repo=0

tail -n +2 "$INPUT_FILE" | while IFS=',' read -r \
project_key project_id project_name project_type \
repo_id repo_slug repo_name repo_state repo_public \
forkable scm default_branch clone_http clone_ssh browse_url description
do
  current_repo=$((current_repo + 1))

  project_key=$(echo "$project_key" | tr -d '"')
  repo_slug=$(echo "$repo_slug" | tr -d '"')

  echo "[$current_repo/$total_repos] Consultando webhooks de repo: $project_key/$repo_slug"

  start=0

  while true; do
    response=$(curl -s -u "$AUTH" \
      "$BASE_URL/rest/api/latest/projects/$project_key/repos/$repo_slug/webhooks?limit=$LIMIT&start=$start")

    size=$(echo "$response" | jq -r '.size // 0')
    is_last=$(echo "$response" | jq -r '.isLastPage // true')
    next_start=$(echo "$response" | jq -r '.nextPageStart // empty')

    echo "  Página start=$start -> webhooks encontrados: $size"

    echo "$response" | jq -c '.values[]?' | while read -r webhook; do
      jq -n -r \
        --arg scope "REPOSITORY" \
        --arg project_key "$project_key" \
        --arg repo_slug "$repo_slug" \
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
          $repo_slug,
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
