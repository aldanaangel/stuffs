#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://bitbucket.miempresa.com"
AUTH="usuario:token"
LIMIT=100
OUTPUT="bitbucket_inventory.csv"

echo '"project_key","project_id","project_name","project_type","repo_id","repo_slug","repo_name","repo_state","repo_public","forkable","scm","default_branch","clone_http","clone_ssh","browse_url","description"' > "$OUTPUT"

project_start=0
project_counter=0
repo_global_counter=0

echo "Iniciando inventario Bitbucket..."
echo "Archivo de salida: $OUTPUT"
echo

while true; do
  echo "Consultando página de proyectos start=$project_start..."

  projects_response=$(curl -s -u "$AUTH" \
    "$BASE_URL/rest/api/latest/projects?limit=$LIMIT&start=$project_start")

  project_page_size=$(echo "$projects_response" | jq -r '.size // 0')
  project_is_last=$(echo "$projects_response" | jq -r '.isLastPage // true')
  project_next_start=$(echo "$projects_response" | jq -r '.nextPageStart // empty')

  echo "Proyectos encontrados en esta página: $project_page_size"
  echo

  while read -r project; do
    project_counter=$((project_counter + 1))

    project_key=$(echo "$project" | jq -r '.key')
    project_id=$(echo "$project" | jq -r '.id')
    project_name=$(echo "$project" | jq -r '.name // ""')
    project_type=$(echo "$project" | jq -r '.type // ""')

    echo "[$project_counter] Procesando proyecto: $project_key - $project_name"

    repo_start=0
    repo_counter_project=0

    while true; do
      echo "  Consultando repos de $project_key start=$repo_start..."

      repos_response=$(curl -s -u "$AUTH" \
        "$BASE_URL/rest/api/latest/projects/$project_key/repos?limit=$LIMIT&start=$repo_start")

      repo_page_size=$(echo "$repos_response" | jq -r '.size // 0')
      repo_is_last=$(echo "$repos_response" | jq -r '.isLastPage // true')
      repo_next_start=$(echo "$repos_response" | jq -r '.nextPageStart // empty')

      echo "  Repos encontrados en esta página: $repo_page_size"

      while read -r repo; do
        repo_counter_project=$((repo_counter_project + 1))
        repo_global_counter=$((repo_global_counter + 1))

        repo_id=$(echo "$repo" | jq -r '.id')
        repo_slug=$(echo "$repo" | jq -r '.slug')
        repo_name=$(echo "$repo" | jq -r '.name // ""')
        repo_state=$(echo "$repo" | jq -r '.state // ""')
        repo_public=$(echo "$repo" | jq -r '.public // ""')
        forkable=$(echo "$repo" | jq -r '.forkable // ""')
        scm=$(echo "$repo" | jq -r '.scmId // ""')
        description=$(echo "$repo" | jq -r '.description // ""')

        echo "    [$repo_global_counter] Repo: $project_key/$repo_slug"

        clone_http=$(echo "$repo" | jq -r '.links.clone[]? | select(.name=="http") | .href' | head -n1)
        clone_ssh=$(echo "$repo" | jq -r '.links.clone[]? | select(.name=="ssh") | .href' | head -n1)
        browse_url=$(echo "$repo" | jq -r '.links.self[0].href // ""')

        default_branch=$(curl -s -u "$AUTH" \
          "$BASE_URL/rest/api/latest/projects/$project_key/repos/$repo_slug/branches/default" \
          | jq -r '.displayId // .id // ""')

        jq -n -r \
          --arg project_key "$project_key" \
          --arg project_id "$project_id" \
          --arg project_name "$project_name" \
          --arg project_type "$project_type" \
          --arg repo_id "$repo_id" \
          --arg repo_slug "$repo_slug" \
          --arg repo_name "$repo_name" \
          --arg repo_state "$repo_state" \
          --arg repo_public "$repo_public" \
          --arg forkable "$forkable" \
          --arg scm "$scm" \
          --arg default_branch "$default_branch" \
          --arg clone_http "$clone_http" \
          --arg clone_ssh "$clone_ssh" \
          --arg browse_url "$browse_url" \
          --arg description "$description" \
          '[
            $project_key,
            $project_id,
            $project_name,
            $project_type,
            $repo_id,
            $repo_slug,
            $repo_name,
            $repo_state,
            $repo_public,
            $forkable,
            $scm,
            $default_branch,
            $clone_http,
            $clone_ssh,
            $browse_url,
            $description
          ] | @csv' >> "$OUTPUT"

      done < <(echo "$repos_response" | jq -c '.values[]?')

      if [[ "$repo_is_last" == "true" ]]; then
        break
      fi

      repo_start="$repo_next_start"
    done

    echo "  Proyecto $project_key finalizado. Repos procesados: $repo_counter_project"
    echo

  done < <(echo "$projects_response" | jq -c '.values[]?')

  if [[ "$project_is_last" == "true" ]]; then
    break
  fi

  project_start="$project_next_start"
done

echo
echo "Inventario finalizado."
echo "Proyectos procesados: $project_counter"
echo "Repositorios procesados: $repo_global_counter"
echo "Archivo generado: $OUTPUT"
