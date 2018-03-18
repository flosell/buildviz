#!/bin/bash
# Full test run
set -e

SCRIPT_DIR=$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)
TMP_DIR="/tmp/buildviz.$$"

function wait_for_server() {
    URL=$1
    until $(curl --output /dev/null --silent --head --fail $URL); do
        printf '.'
        sleep 5
    done
}

function unit_test {
    "$SCRIPT_DIR/lein" test
}

function go_sync_end2end {
    cd "$SCRIPT_DIR/test/gosync"
    ./mock.sh &
    MOCK_SERVER_PID=$!
    cd -

    BUILDVIZ_DATA_DIR=$TMP_DIR "$SCRIPT_DIR/lein" do deps, ring server-headless 3335 > /dev/null &
    BUILDVIZ_SERVER_PID=$!
    wait_for_server http://localhost:3335

    "$SCRIPT_DIR/lein" run -m buildviz.go.sync http://localhost:3334 --buildviz http://localhost:3335 --from 2014-01-01
    pkill -P $MOCK_SERVER_PID
    pkill -P $BUILDVIZ_SERVER_PID
}

function end2end_test {
    yes | "$SCRIPT_DIR/examples/runSeedDataExample.sh"
}

function main {
    unit_test
    go_sync_end2end
    end2end_test
}

main