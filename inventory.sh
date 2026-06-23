#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://bitbucket.miempresa.com"
AUTH="usuario:token"
OUTPUT="bitbucket_inventory.csv"

echo '"project_key","project_id","project_name","project_type","repo_id","repo_slug","repo_name","repo_state","repo_public","forkable","scm","default_branch","clone_http","clone_ssh","browse_url","description"' > "$OUTPUT"

projects_json=$(curl -s -u "$AUTH" \
  "$BASE_URL/rest/api/latest/projects?limit=1000")

echo "$projects_json" | jq -c '.values[]' | while read -r project; do
  project_key=$(echo "$project" | jq -r '.key')
  project_id=$(echo "$project" | jq -r '.id')
  project_name=$(echo "$project" | jq -r '.name // ""')
  project_type=$(echo "$project" | jq -r '.type // ""')

  repos_json=$(curl -s -u "$AUTH" \
    "$BASE_URL/rest/api/latest/projects/$project_key/repos?limit=1000")

  echo "$repos_json" | jq -c '.values[]' | while read -r repo; do
    repo_id=$(echo "$repo" | jq -r '.id')
    repo_slug=$(echo "$repo" | jq -r '.slug')
    repo_name=$(echo "$repo" | jq -r '.name // ""')
    repo_state=$(echo "$repo" | jq -r '.state // ""')
    repo_public=$(echo "$repo" | jq -r '.public // ""')
    forkable=$(echo "$repo" | jq -r '.forkable // ""')
    scm=$(echo "$repo" | jq -r '.scmId // ""')
    description=$(echo "$repo" | jq -r '.description // ""')

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
  done
done

echo "Inventario generado: $OUTPUT"
