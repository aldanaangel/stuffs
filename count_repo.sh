#!/usr/bin/env bash
set -euo pipefail

BASE_URL="https://bitbucket.miempresa.com"
AUTH="usuario:token"
LIMIT=100

total_projects=0
total_repos=0
start=0

echo "Consultando proyectos en Bitbucket..."
echo

while true; do
  response=$(curl -s -u "$AUTH" \
    "$BASE_URL/rest/api/latest/projects?limit=$LIMIT&start=$start")

  page_size=$(echo "$response" | jq -r '.size // 0')
  is_last=$(echo "$response" | jq -r '.isLastPage // true')
  next_start=$(echo "$response" | jq -r '.nextPageStart // empty')

  total_projects=$((total_projects + page_size))

  echo "Página proyectos start=$start -> encontrados en página: $page_size"

  echo "$response" | jq -c '.values[]?' | while read -r project; do
    project_key=$(echo "$project" | jq -r '.key')
    project_name=$(echo "$project" | jq -r '.name // ""')

    repo_count=0
    repo_start=0

    while true; do
      repos_response=$(curl -s -u "$AUTH" \
        "$BASE_URL/rest/api/latest/projects/$project_key/repos?limit=$LIMIT&start=$repo_start")

      repos_page_size=$(echo "$repos_response" | jq -r '.size // 0')
      repos_is_last=$(echo "$repos_response" | jq -r '.isLastPage // true')
      repos_next_start=$(echo "$repos_response" | jq -r '.nextPageStart // empty')

      repo_count=$((repo_count + repos_page_size))

      if [[ "$repos_is_last" == "true" ]]; then
        break
      fi

      repo_start="$repos_next_start"
    done

    echo "Proyecto: $project_key - $project_name -> repos: $repo_count"
    echo "$repo_count" >> /tmp/bitbucket_repo_counts.tmp
  done

  if [[ "$is_last" == "true" ]]; then
    break
  fi

  start="$next_start"
done

if [[ -f /tmp/bitbucket_repo_counts.tmp ]]; then
  total_repos=$(awk '{sum += $1} END {print sum}' /tmp/bitbucket_repo_counts.tmp)
  rm -f /tmp/bitbucket_repo_counts.tmp
fi

echo
echo "Resumen:"
echo "Total proyectos: $total_projects"
echo "Total repositorios: $total_repos"
