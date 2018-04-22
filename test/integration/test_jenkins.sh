#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly MAPPING_SOURCE="${SCRIPT_DIR}/jenkins.tar.gz"
readonly DATA_DIR="${SCRIPT_DIR}/../../examples/data"

readonly WIREMOCK_PORT="3341"
readonly BUILDVIZ_PORT="3351"

readonly WIREMOCK_BASE_URL="http://localhost:${WIREMOCK_PORT}"
readonly BUILDVIZ_BASE_URL="http://localhost:${BUILDVIZ_PORT}"
readonly SYNC_URL="$WIREMOCK_BASE_URL"

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
    "${SCRIPT_DIR}/../../lein" run -m buildviz.jenkins.sync "$SYNC_URL" --buildviz="$BUILDVIZ_BASE_URL" --from 2000-01-01
}

clean_up() {
    stop_buildviz
    stop_wiremock
}

ensure_mappings() {
    if [[ ! -e "$MAPPING_SOURCE" ]]; then
        echo "Please run ./record_jenkins.sh first"
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
}

main
