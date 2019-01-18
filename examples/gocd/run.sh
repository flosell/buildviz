#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly BASE_URL="http://localhost:8153/go"

wait_for_server() {
    local url=$1
    echo -n " waiting for ${url}"
    until curl --output /dev/null --silent --head --fail "$url"; do
        printf '.'
        sleep 5
    done
}

announce() {
    local text="$1"
    echo -ne "\033[1;30m"
    echo -n "$text"
    echo -ne "\033[0m"
}

hint_at_logs() {
    # shellcheck disable=SC2181
    if [[ "$?" -ne 0 ]]; then
        echo
        echo "Logs are in ${TMP_LOG}"
    fi
}

docker_compose() {
    (
        cd "$SCRIPT_DIR"
        docker-compose "$@"
    )
}

container_exists() {
    if [[ -z $(docker_compose ps -q) ]]; then
        return 1
    else
        return 0
    fi
}

provision_gocd() {
    local server_path="${SCRIPT_DIR}/server"
    # Wonky workaround for trying to boot up server image with minimal config, but no agent registration
    mkdir -p "${server_path}/config"
    cp "$server_path"/*.xml "${server_path}/config"

    docker_compose up --no-start
}

start_gocd() {
    announce "Starting docker image"
    docker_compose up -d &> "$TMP_LOG"

    wait_for_server "$BASE_URL"
    echo " done"
    rm "$TMP_LOG"
}

wait_for_pipeline_to_be_schedulable() {
    until curl --silent --fail "${BASE_URL}/api/pipelines/Example/status" | grep '"schedulable":true' > /dev/null; do
        printf '.'
        sleep 5
    done
}

goal_start() {
    local run
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_gocd
        start_gocd

        for run in 1 2 3 4 5; do
            announce "Scheduling run ${run}"
            wait_for_pipeline_to_be_schedulable
            curl --fail --silent -X POST "${BASE_URL}/api/pipelines/Example/schedule" -H 'Accept: application/vnd.go.cd.v1+json' -H "X-GoCD-Confirm: true" > /dev/null
            echo
        done
    else
        start_gocd
    fi
}


goal_stop() {
    announce "Stopping docker image"
    docker_compose stop &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_destroy() {
    announce "Destroying docker container"
    docker_compose down &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

goal_purge() {
    announce "Purging docker images"
    docker images -q gocd/gocd-server | xargs docker rmi &> "$TMP_LOG"
    docker images -q gocd/gocd-agent-alpine-3.8 | xargs docker rmi &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        "goal_$1"
    else
        echo "usage: $0 (start|stop|destroy|purge)"
    fi
}

main "$@"
