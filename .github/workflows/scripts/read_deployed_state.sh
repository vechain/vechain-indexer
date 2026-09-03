#!/usr/bin/env bash
# What each colour runs, as one JSON document keyed by colour:
#   {"prod-blue": {"state": "up|stopped|absent",
#                  "services": {"main": {"api": {"tag", "desired", "running"}, ...}, ...}}}
# A colour is "up" when both indexer services have running tasks: the indexer
# is what keeps the colour's data current. Image tags come from the service's
# task definition, since the family's latest may be a revision no rollout completed.
# "release" is the tag whose terraform/api was last applied there (recorded by
# plan-or-apply-terraform.yml); an unreadable marker is empty, never fatal.
set -euo pipefail

COLOURS="${COLOURS:?space-separated colours, e.g. 'prod-blue prod-green'}"

running_tag() {
    aws ecs describe-task-definition \
        --task-definition "$1" \
        --query 'taskDefinition.containerDefinitions[0].image' \
        --output text 2>/dev/null \
    | awk -F: 'NF>1 {print $NF}'
}

err=$(mktemp)
trap 'rm -f "$err"' EXIT

deployed_release() {
    local value
    if value=$(aws ssm get-parameter --name "/veworld/$1/deployed-release" \
        --query 'Parameter.Value' --output text 2>"$err"); then
        echo "$value"
    elif ! grep -q ParameterNotFound "$err"; then
        echo "::warning::Could not read $1's deployed release: $(tr -d '\n' <"$err")"
    fi
}

state='{}'
for colour in $COLOURS; do
    names=()
    for net in main test; do
        for svc in api indexer; do
            names+=("${colour}-veworld-${net}-${svc}-service")
        done
    done

    # Only a missing cluster means absent; any other failure must not be
    # mistaken for one, since absent can lead to a restore.
    # shellcheck disable=SC2016  # JMESPath literal, not a shell expansion
    if ! desc=$(aws ecs describe-services \
        --cluster "${colour}-veworld-cluster" \
        --services "${names[@]}" \
        --query 'services[?status==`ACTIVE`].{name:serviceName,desired:desiredCount,running:runningCount,taskDef:taskDefinition}' \
        --output json 2>"$err"); then
        if grep -q ClusterNotFoundException "$err"; then
            desc='[]'
        else
            cat "$err" >&2
            echo "::error::Could not read ${colour}'s ECS services; refusing to guess its state."
            exit 1
        fi
    fi

    services='{}'
    while IFS=$'\t' read -r name desired running task_def; do
        [ -n "$name" ] || continue
        [[ "$name" =~ -veworld-(main|test)-(api|indexer)-service$ ]] || continue
        net="${BASH_REMATCH[1]}" svc="${BASH_REMATCH[2]}"
        tag=""
        if [ -n "$task_def" ] && [ "$task_def" != "None" ]; then
            tag=$(running_tag "$task_def")
        fi
        services=$(jq -c --arg n "$net" --arg s "$svc" --arg t "$tag" \
            --argjson d "${desired:-0}" --argjson r "${running:-0}" \
            '.[$n][$s] = {tag: $t, desired: $d, running: $r}' <<<"$services")
    done < <(jq -r '.[] | [.name, .desired, .running, .taskDef] | @tsv' <<<"$desc")

    if [ "$(jq 'length' <<<"$desc")" -eq 0 ]; then
        colour_state=absent
    elif [ "$(jq '[.main.indexer.running, .test.indexer.running] | all(. != null and . > 0)' <<<"$services")" = true ]; then
        colour_state=up
    else
        colour_state=stopped
    fi

    release=$(deployed_release "$colour")
    state=$(jq -c --arg c "$colour" --arg st "$colour_state" --arg r "$release" --argjson s "$services" \
        '.[$c] = {state: $st, release: $r, services: $s}' <<<"$state")
done

echo "STATE=${state}" >> "${GITHUB_OUTPUT:?}"
jq . <<<"$state"
