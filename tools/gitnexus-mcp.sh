#!/bin/zsh
set -euo pipefail

find_cached_gitnexus() {
  local package_json=""
  local package_dir=""
  local bin_path=""
  local best_bin=""
  local best_mtime=0
  local mtime=0

  for package_json in "$HOME"/.npm/_npx/*/node_modules/gitnexus/package.json; do
    [[ -f "$package_json" ]] || continue

    package_dir="${package_json:h}"
    bin_path="$package_dir/../.bin/gitnexus"
    [[ -x "$bin_path" ]] || continue

    mtime="$(stat -f '%m' "$package_json" 2>/dev/null || echo 0)"
    if (( mtime > best_mtime )); then
      best_mtime="$mtime"
      best_bin="$bin_path"
    fi
  done

  [[ -n "$best_bin" ]] && print -r -- "$best_bin"
}

main() {
  local cached_bin=""
  cached_bin="$(find_cached_gitnexus || true)"

  if [[ -n "$cached_bin" ]]; then
    exec "$cached_bin" mcp
  fi

  exec npx -y gitnexus@latest mcp
}

main "$@"
