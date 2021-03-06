#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly MAPPING_SOURCE="${SCRIPT_DIR}/teamcity.tar.gz"
readonly DATA_DIR="${SCRIPT_DIR}/../../examples/data"

readonly WIREMOCK_PORT="3342"
readonly BUILDVIZ_PORT="3352"

readonly WIREMOCK_BASE_URL="http://localhost:${WIREMOCK_PORT}"
readonly BUILDVIZ_BASE_URL="http://localhost:${BUILDVIZ_PORT}"
readonly SYNC_URL="http://localhost:${WIREMOCK_PORT}"
readonly SYNC_USER="admin"
readonly SYNC_PASSWORD="admin"


readonly MAPPING_TMP_DIR="/tmp/record.wiremock.$$"

start_buildviz() {
    PORT="$BUILDVIZ_PORT" "${DATA_DIR}/run_buildviz.sh" start
}

stop_buildviz() {
    "${DATA_DIR}/run_buildviz.sh" stop
}

start_wiremock() {
    mkdir "$MAPPING_TMP_DIR"
    tar -xzf "$MAPPING_SOURCE" -C "$MAPPING_TMP_DIR"

    "${SCRIPT_DIR}/run_wiremock.sh" install
    ROOT_DIR="$MAPPING_TMP_DIR" PORT="$WIREMOCK_PORT" "${SCRIPT_DIR}/run_wiremock.sh" start
}

stop_wiremock() {
    "${SCRIPT_DIR}/run_wiremock.sh" stop

    rm -rf "$MAPPING_TMP_DIR"
}

sync_builds() {
    TEAMCITY_USER="$SYNC_USER" TEAMCITY_PASSWORD="$SYNC_PASSWORD" "${SCRIPT_DIR}/../../lein" run -m buildviz.teamcity.sync "$SYNC_URL" --buildviz="$BUILDVIZ_BASE_URL" --from 2000-01-01 -p SimpleSetup
}

ensure_user_agent() {
    local count_request='{"method": "GET", "url": "/httpAuth/app/rest/projects/SimpleSetup", "headers": {"User-Agent": {"matches": "buildviz.*"}}}'
    local count_response
    count_response=$(echo "$count_request" | curl -s -X POST -d@- "${WIREMOCK_BASE_URL}/__admin/requests/count")
    if ! grep '"count" : 1' <<<"$count_response" > /dev/null; then
        echo "User agent not found:"
        echo "$count_response"
        exit 1
    fi

}

ensure_basic_auth() {
    local count_request='{"method": "GET", "url": "/httpAuth/app/rest/projects/SimpleSetup", "headers": {"Authorization": {"matches": "Basic YWRtaW46YWRtaW4="}}}'
    local count_response
    count_response=$(echo "$count_request" | curl -s -X POST -d@- "${WIREMOCK_BASE_URL}/__admin/requests/count")
    if ! grep '"count" : 1' <<<"$count_response" > /dev/null; then
        echo "Basic auth not found:"
        echo "$count_response"
        exit 1
    fi

}

clean_up() {
    stop_buildviz
    stop_wiremock
}

ensure_mappings() {
    if [[ ! -e "$MAPPING_SOURCE" ]]; then
        echo "Please run ./record_teamcity.sh first"
        exit 1
    fi
}

main() {
    ensure_mappings

    # Handle Ctrl+C and errors
    trap clean_up EXIT

    start_buildviz
    start_wiremock

    sync_builds

    ensure_user_agent
    ensure_basic_auth
}

main
