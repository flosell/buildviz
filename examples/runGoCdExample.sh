#!/bin/bash
set -eo pipefail

readonly SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)

readonly BUILDVIZ_PORT=3333
readonly BUILDVIZ_PATH="http://localhost:${BUILDVIZ_PORT}"

ensure_port_available() {
    if curl --output /dev/null --silent --head --fail "${BUILDVIZ_PATH}"; then
        echo "Please stop the application running on port $PORT before continuing"
        exit 1
    fi
}

clean_up() {
    "${SCRIPT_DIR}/go/run.sh" stop
    "${SCRIPT_DIR}/data/run_buildviz.sh" stop
}

main() {
    ensure_port_available

    echo "This example will download and install GoCD in a VirtualBox and then sync its output to buildviz"
    echo
    echo "Press any key to continue"

    read -n 1

    # Handle Ctrl+C
    trap clean_up EXIT

    "${SCRIPT_DIR}/go/run.sh" start

    PORT="$BUILDVIZ_PORT" "${SCRIPT_DIR}/data/run_buildviz.sh" start

    echo "Syncing job history..."
    "${SCRIPT_DIR}/../lein" run -m buildviz.go.sync http://localhost:8153/go --buildviz="${BUILDVIZ_PATH}" --from 2014-06-01

    echo "Done..."
    echo
    echo "Point your browser to ${BUILDVIZ_PATH}"
    echo
    echo "Later, press any key to stop the server and bring down the vagrant box"

    read -n 1
}

main
