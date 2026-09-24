#!/usr/bin/env bash
set -euo pipefail

# Current script dir
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

mkdir -p "$DIR/ongeki" "$DIR/mai2" "$DIR/chu3" "$DIR/wacca"

DATA_HOST="${DATA_HOST:-http://asset.maimaidx.top}"

download_required() {
  local url="$1"
  local target="$2"
  local temporary="${target}.tmp"
  if ! curl -fsSL --retry 3 --connect-timeout 10 --max-time 120 "$url" -o "$temporary"; then
    rm -f "$temporary"
    return 1
  fi
  if [[ ! -s "$temporary" ]]; then
    rm -f "$temporary"
    echo "Required metadata download returned an empty file: $url" >&2
    return 1
  fi
  mv "$temporary" "$target"
}

download_optional() {
  local url="$1"
  local target="$2"
  local temporary="${target}.tmp"
  if curl -fsSL --retry 3 --connect-timeout 10 --max-time 120 "$url" -o "$temporary" && [[ -s "$temporary" ]]; then
    mv "$temporary" "$target"
  else
    rm -f "$temporary"
    if [[ ! -s "$target" ]]; then
      rm -f "$target"
    fi
    echo "Optional metadata unavailable; continuing: $url" >&2
  fi
}

download_required "${DATA_HOST}/d/ongeki/00/all-music.json" "$DIR/ongeki/music.json"
download_required "${DATA_HOST}/d/mai2/00/all-music.json" "$DIR/mai2/music.json"
download_required "${DATA_HOST}/d/chu3/00/all-music.json" "$DIR/chu3/music.json"
download_optional "${DATA_HOST}/d/mai2/00/all-items.json" "$DIR/mai2/items.json"
download_optional "${DATA_HOST}/d/wacca/00/all-music.json" "$DIR/wacca/music.json"
