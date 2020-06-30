#!/usr/bin/env bash""

trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT
HOST="http://localhost:8098/graphql"

# Color codes via https://stackoverflow.com/questions/5947742/how-to-change-the-output-color-of-echo-in-linux#5947802
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color


log() {
  # echo in color
  echo -e "${GREEN}${1}${NC}"
}

rlog() {
  # echo in color
  echo -e "${RED}${1}${NC}"
}

log "Starting HGQL endpoint in the background"
java -jar ./hypergraphql-1.0.3-exe.jar --config "query_test/config_hgql_1.0.3.json" >> query_test/hgql_1.0.3.log &
log "Interface running at ${HOST}"

sleep 30s
HGQLQ1='{"query":"{\n  ex_car_GET{\n    ex_model\n    _id\n    ex_ownedBy{\n      _id\n    }\n  }\n}","variables":null,"operationName":null}'

HGQLQ2='{"query":"{\n  ex_Person_GET(limit:100){\n    _id\n    ex_name\n    ex_surname\n    ex_age\n  }\n}","variables":null,"operationName":null}'

HGQLQ3='{"query":"{\n  ex_Person_GET_BY_ID(uris:[\"http://example.org/person_999\"]){\n    _id\n    ex_relatedWith{\n      _id\n      ex_relatedWith{\n        _id\n        ex_relatedWith{\n          _id\n        }\n      }\n    }\n  }\n}","variables":null,"operationName":null}'
HGQLQ4=""
HGQLQ5=""

ROUNDS=1000


Query(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    curl -s --location --request POST 'localhost:8098/graphql' \
    --header 'Content-Type: application/json' \
    --data-raw "${QUERY}" > query_test/result_hgql_1.0.3_query_${NR}.log
    done
    DIFF=$(echo "$(date +%s.%N) - $START" | bc)
    AVG=$(echo "scale=4; $DIFF/$ROUNDS" | bc)
    log "Execution time total: ${DIFF} seconds"
    log "Execution time AVG: ${AVG} seconds\n\n"
}
log "Each query will be queried ${ROUNDS} times to estimate the average query execution time."
log "Starting Query 1:\n"
Query "1" "$HGQLQ1"

log "Starting Query 2"
Query "2" "$HGQLQ2"

log "Starting Query 3"
Query "3" "$HGQLQ3"

wait