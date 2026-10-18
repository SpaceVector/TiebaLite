#!/usr/bin/env bash
set -euo pipefail

: "${GH_REPO:?}" "${VERSION_NAME:?}" "${VERSION_CODE:?}" "${GITHUB_SHA:?}"

if [[ "${GITHUB_REF:-}" != "refs/heads/4.0-dev" || "${GITHUB_EVENT_NAME:-}" == "pull_request" ]]; then
    echo "Release publishing is only allowed for the 4.0-dev branch outside pull requests."
    exit 0
fi

if [[ "${SIGNING_CONFIGURED:-false}" != "true" ]]; then
    echo "::error::Publishing requires the verified fixed Release signing key."
    exit 1
fi

tag="v${VERSION_NAME}"
git check-ref-format "refs/tags/$tag"

if [[ "$#" -eq 0 ]]; then
    echo "::error::No release assets were provided."
    exit 1
fi
for asset in "$@"; do
    if [[ "$asset" != *.apk ]]; then
        echo "::error::Only APK files may be published: $asset"
        exit 1
    fi
    if [[ ! -s "$asset" ]]; then
        echo "::error::Missing or empty release asset: $asset"
        exit 1
    fi
done

repo_url="${GITHUB_SERVER_URL:-https://github.com}/${GH_REPO}"
release_url="$repo_url/releases/tag/$tag"
draft=$(gh release view "$tag" --json isDraft --jq '.isDraft' 2>/dev/null || true)
if [[ "$draft" == "false" ]]; then
    echo "Release $tag is already published; leaving its assets and notes unchanged."
    printf 'Release 已存在：[查看版本](%s)\n' "$release_url" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
    exit 0
fi

# Only compare against published releases reachable from this build's commit.
previous_tag=""
release_tags=$(gh release list --exclude-drafts --limit 100 --json tagName --jq '.[].tagName')
while IFS= read -r candidate; do
    if [[ -n "$candidate" && "$candidate" != "$tag" ]] &&
        git merge-base --is-ancestor "refs/tags/$candidate" "$GITHUB_SHA" 2>/dev/null; then
        previous_tag="$candidate"
        break
    fi
done <<< "$release_tags"
if [[ -z "$previous_tag" ]]; then
    previous_tag=$(git describe --tags --abbrev=0 "${GITHUB_SHA}^" 2>/dev/null || true)
fi

range="$GITHUB_SHA"
changelog_url="$repo_url/commits/$GITHUB_SHA"
if [[ -n "$previous_tag" ]]; then
    range="${previous_tag}..${GITHUB_SHA}"
    changelog_url="$repo_url/compare/${previous_tag}...${GITHUB_SHA}"
fi

notes=$(mktemp)
trap 'rm -f "$notes"' EXIT
{
    printf '## 构建信息\n\n- 版本：`%s`（`%s`）\n- 源码：[%s](%s/commit/%s)\n' \
        "$VERSION_NAME" "$VERSION_CODE" "${GITHUB_SHA:0:7}" "$repo_url" "$GITHUB_SHA"
    printf '\n## 更新日志\n\n'
    git log --no-merges --max-count=100 --format='- %s (%h)' "$range"
    printf '\n[完整更新记录](%s)（上方最多展示 100 条非合并提交）\n' "$changelog_url"
    printf '\n[GitHub Actions 构建记录](%s/actions/runs/%s)\n' "$repo_url" "${GITHUB_RUN_ID:?}"
} > "$notes"

prerelease=false
if [[ "${VERSION_NAME%%+*}" == *-* ]]; then
    prerelease=true
fi
if [[ "$draft" == "true" ]]; then
    # Recover a draft left by an interrupted asset upload without modifying a published release.
    gh release upload "$tag" "$@" --clobber
    gh release edit "$tag" --draft=false --prerelease="$prerelease" \
        --title "Tieba Lite $VERSION_NAME" --notes-file "$notes"
else
    gh release create "$tag" "$@" --target "$GITHUB_SHA" --prerelease="$prerelease" \
        --title "Tieba Lite $VERSION_NAME" --notes-file "$notes"
fi
printf 'Release 已发布：[查看版本](%s)\n' "$release_url" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
