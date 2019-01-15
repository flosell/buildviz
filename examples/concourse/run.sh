#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly TMP_LOG="/tmp/run.$$.log"
readonly BASE_URL="http://localhost:8080"

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

provision_container() {
    docker_compose up --no-start
}

start_server() {
    announce "Starting docker image"
    docker_compose up -d &> "$TMP_LOG"

    wait_for_server "$BASE_URL"
    echo " done"
    rm "$TMP_LOG"
}

provision_pipeline() {
    local fly_bin="/tmp/fly.$$"
    curl -vL "${BASE_URL}/api/v1/cli?arch=amd64&platform=darwin" -o "$fly_bin" &> "$TMP_LOG"
    chmod a+x "$fly_bin" > "$TMP_LOG"

    "$fly_bin" -t buildviz login -c "$BASE_URL" -u user -p password > "$TMP_LOG"
    "$fly_bin" -t buildviz set-pipeline -p pipeline -c pipeline.yml -n > "$TMP_LOG"
    rm "$fly_bin"
}

goal_start() {
    if ! container_exists; then
        announce "Provisioning docker image"
        echo
        provision_container
        start_server
        provision_pipeline
    else
        start_server
    fi
}

goal_stop() {
    announce "Stopping docker image"
    docker_compose stop &> "$TMP_LOG"
    echo " done"
    rm "$TMP_LOG"
}

main() {
    trap hint_at_logs EXIT

    if type -t "goal_$1" &>/dev/null; then
        "goal_$1"
    else
        echo "usage: $0 (start|stop)"
    fi
}

main "$@"
