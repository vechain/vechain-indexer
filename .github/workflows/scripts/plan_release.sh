#!/usr/bin/env bash
# Decides where a release goes and what it needs, from git and the deployed
# state alone: no credentials. Emits the plan as job outputs and a summary.
#
#   dead colour up                      -> dead (it is the colour being staged)
#   dead colour cold, indexer changed   -> dead, restored from live snapshots first
#   dead colour cold, indexer unchanged -> live
#
# "Indexer changed" means its image content differs from what the live colour
# runs, or terraform/api differs from the release last applied there (its image
# tag stands in when no release was recorded). Deploying that to live pauses
# indexing, so an explicit TARGET_INPUT=live is refused; the rest may be overridden.
set -euo pipefail

VERSION="${VERSION:?}"
TARGET_INPUT="${TARGET_INPUT:?auto|live|dead}"
SKIP_RESTORE="${SKIP_RESTORE:-false}"
STATE="${STATE:?JSON from read_deployed_state.sh}"
MAINNET_LIVE="${MAINNET_LIVE:?}" MAINNET_DEAD="${MAINNET_DEAD:?}"
TESTNET_LIVE="${TESTNET_LIVE:?}" TESTNET_DEAD="${TESTNET_DEAD:?}"

scripts=$(dirname "${BASH_SOURCE[0]}")

if [ "$MAINNET_LIVE" != "$TESTNET_LIVE" ]; then
    echo "::error::Mainnet is live on ${MAINNET_LIVE} but testnet on ${TESTNET_LIVE}. Run 'Switch Live Environment' for one network to align them, then deploy."
    exit 1
fi
live="$MAINNET_LIVE"
dead="$MAINNET_DEAD"
dead_state=$(jq -r --arg c "$dead" '.[$c].state // "absent"' <<<"$STATE")

tag_of() { # colour net svc
    jq -r --arg c "$1" --arg n "$2" --arg s "$3" '.[$c].services[$n][$s].tag // empty' <<<"$STATE"
}

terraform_baseline() { # colour -> release last applied there, else its indexer tag
    local release
    release=$(jq -r --arg c "$1" '.[$c].release // empty' <<<"$STATE")
    echo "${release:-$(tag_of "$1" main indexer)}"
}

known_tag() {
    [ -n "$1" ] && git rev-parse -q --verify "${1}^{tree}" >/dev/null
}

hash_at() { # svc tag -> content hash, or empty for a tag git does not know
    known_tag "$2" || return 0
    "$scripts/content_hash.sh" "$1" "$2"
}

terraform_changed_since() { # tag -> true when terraform/api differs, or no tag to compare
    known_tag "$1" || return 0
    ! git diff --quiet "$1" "$VERSION" -- terraform/api
}

# True when either network's running image differs in content from VERSION.
image_changed_vs() { # colour svc
    local net cur old new
    new=$(hash_at "$2" "$VERSION")
    for net in main test; do
        cur=$(tag_of "$1" "$net" "$2")
        old=$(hash_at "$2" "$cur")
        [ -n "$old" ] && [ "$old" = "$new" ] || return 0
    done
    return 1
}

join() { local IFS='; '; echo "$*"; }

live_indexer_tag=$(tag_of "$live" main indexer)
live_baseline=$(terraform_baseline "$live")
indexer_reasons=()
if image_changed_vs "$live" indexer; then
    indexer_reasons+=("image content differs from ${live_indexer_tag:-what live runs}")
fi
if terraform_changed_since "$live_baseline"; then
    indexer_reasons+=("terraform/api changed since ${live_baseline:-the last recorded release}")
fi
indexer_changed=false
[ "${#indexer_reasons[@]}" -eq 0 ] || indexer_changed=true
api_changed=false
if image_changed_vs "$live" api; then api_changed=true; fi

case "$dead_state" in
    up) dead_desc="running" ;;
    stopped) dead_desc="stopped" ;;
    *) dead_desc="not deployed" ;;
esac

if [ "$dead_state" = up ]; then
    recommended=dead
    why="${dead} is running, so it is the colour being staged for the next cutover."
elif [ "$indexer_changed" = true ]; then
    recommended=dead
    why="The indexer changes against ${live} ($(join "${indexer_reasons[@]}")). Deploying it to live would pause indexing."
else
    recommended=live
    why="Nothing that restarts the indexer changes against ${live}, so live takes it with no indexing gap."
fi

case "$TARGET_INPUT" in
    auto) target_kind="$recommended" ;;
    live | dead) target_kind="$TARGET_INPUT" ;;
    *) echo "::error::Unknown target '${TARGET_INPUT}'"; exit 1 ;;
esac

if [ "$target_kind" = live ] && [ "$indexer_changed" = true ]; then
    echo "::error::Refusing to deploy to live: the indexer changes against ${live} ($(join "${indexer_reasons[@]}")). Deploy to dead and switch DNS once it has synced."
    exit 1
fi

if [ "$target_kind" = live ]; then
    target_colour="$live"
else
    target_colour="$dead"
fi

restore=false
restore_note="no; ${target_colour} is live"
if [ "$target_kind" = dead ]; then
    if [ "$dead_state" = up ]; then
        restore_note="no; ${dead} is running and its indexers have kept its data current"
    elif [ "$SKIP_RESTORE" = true ]; then
        restore_note="no; skipped by request. ${dead} indexes on from whatever its Atlas clusters hold: a stale checkpoint for a stopped colour, nothing for a torn-down one"
    else
        restore=true
        restore_note="yes; ${dead} is ${dead_desc}, so its data is stale. Latest ${live} snapshots are restored before its services start"
    fi
fi

# Image tags per net and service, against what the target runs (or live, for
# a colour with nothing running).
versions='{}'
promote=()
rows=()
staged=true
for net in main test; do
    for svc in api indexer; do
        current=$(tag_of "$target_colour" "$net" "$svc")
        [ -n "$current" ] || current=$(tag_of "$live" "$net" "$svc")
        new_hash=$(hash_at "$svc" "$VERSION")
        old_hash=$(hash_at "$svc" "$current")

        if [ -n "$old_hash" ] && [ "$old_hash" = "$new_hash" ]; then
            chosen="$current"
            note="unchanged since ${current} (${new_hash})"
        else
            chosen="$VERSION"
            promote+=("$svc")
            note="content ${new_hash}"
            staged=false
        fi

        if [ -n "$chosen" ]; then
            versions=$(jq -c --arg n "$net" --arg s "$svc" --arg v "$chosen" \
                '.[$n][$s] = $v' <<<"$versions")
        else
            note="no deployed tag to keep; environment yaml decides"
        fi
        rows+=("| ${net} | ${svc} | ${current:-—} | ${chosen:-<yaml>} | ${note} |")
    done
done

if [ "$target_kind" = dead ] && [ "$dead_state" = up ] && [ "$staged" = true ] \
    && ! terraform_changed_since "$(terraform_baseline "$dead")"; then
    staged_note="${dead} already runs this content. The application apply is a no-op; if a cutover is all you want, run 'Switch Live Environment' instead."
else
    staged_note=""
fi

promote_list=$(printf '%s\n' "${promote[@]+"${promote[@]}"}" \
    | jq -Rc 'select(. != "")' | jq -sc 'unique')

if [ "$target_kind" = dead ]; then
    next_step="Run 'Switch Live Environment' (network: all) once every indexer on ${target_colour} reports fully synced."
else
    next_step="None. ${target_colour} keeps serving traffic and the release is published."
fi

title="Deploy ${VERSION} to ${target_colour} (${target_kind})"
[ "$restore" = false ] || title+=", restoring from ${live} snapshots first"

{
    echo "TARGET_COLOR=${target_colour}"
    echo "TARGET_KIND=${target_kind}"
    echo "LIVE_COLOR=${live}"
    echo "RESTORE=${restore}"
    echo "CONFIRM_TITLE=${title}"
    echo "IMAGE_VERSIONS=${versions}"
    echo "PROMOTE=${promote_list}"
    echo "PROMOTE_NEEDED=$([ "$(jq 'length' <<<"$promote_list")" -gt 0 ] && echo true || echo false)"
} >> "${GITHUB_OUTPUT:?}"

requested="${TARGET_INPUT}"
[ "$TARGET_INPUT" = auto ] || requested="**${TARGET_INPUT}** (recommended: ${recommended})"

{
    echo "### Release plan: ${title}"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| Live colour | ${live} (both networks) |"
    echo "| Dead colour | ${dead}, ${dead_desc} |"
    echo "| Target | ${target_colour} (${target_kind}), requested: ${requested} |"
    echo "| Recommended | ${recommended}: ${why} |"
    echo "| API changed vs live | ${api_changed} |"
    echo "| Indexer changed vs live | ${indexer_changed}$([ "$indexer_changed" = true ] && printf ': %s' "$(join "${indexer_reasons[@]}")") |"
    echo "| Restore first | ${restore_note} |"
    echo "| After the deploy | ${next_step} |"
    echo
    [ -z "$staged_note" ] || { echo "> ${staged_note}"; echo; }
    echo "| net | service | running on ${target_colour} | deploying | note |"
    echo "|---|---|---|---|---|"
    printf '%s\n' "${rows[@]}"
} >> "${GITHUB_STEP_SUMMARY:?}"
