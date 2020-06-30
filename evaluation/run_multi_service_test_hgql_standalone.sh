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
java -jar ./hypergraphql-2.0.0-exe.jar --config "multiple_service_test/config_hgql_standalone.json" >> multiple_service_test/hgql_standalone.log &
log "Interface running at ${HOST}"

sleep 10s
HGQLQ1='{"query":"{\n  ex_Person(_id:[\"http://xmlns.com/foaf/0.1/person_253\",\"http://example.org/person_253\"]){\n    _id\n    ex_name\n  }\n}","variables":null,"operationName":null}'

HGQLQ2='{"query":"{\n  foaf_Person{\n    _id\n    ex_drives{\n      ex_model\n    }\n  }\n}","variables":null,"operationName":null}'

HGQLQ3='{"query":"{\n\tex_Person(_id:[\"http://example.org/person_934\"]){\n    _id\n    ex_relatedWith{\n      _id\n      ex_relatedWith{\n        _id\n        ex_relatedWith{\n          _id\n          ex_relatedWith{\n          \t_id\n        \t}\n        }\n      }\n    }\n  }\n}","variables":null,"operationName":null}'


ROUNDS=100


Query(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    curl -s --location --request POST 'localhost:8098/graphql' \
    --header 'Content-Type: application/json' \
    --data-raw "${QUERY}" >> /dev/null
    done
    DIFF=$(echo "$(date +%s.%N) - $START" | bc)
    AVG=$(echo "scale=4; $DIFF/$ROUNDS" | bc)
    log "Execution time total: ${DIFF} seconds"
    log "Execution time AVG: ${AVG} seconds\n\n"
}



log "Each query will be queried ${ROUNDS} times to estimate the average query execution time."
log "Starting Query 1:\n"
#Query "1" "$HGQLQ1"

log "Starting Query 2:"
Query "2" "$HGQLQ2"

log "Starting Query 3:"
#Query "3" "$HGQLQ3"

log "Finished Evaluation. End the script or use the GraphiQL endpoint for custom queries at http://localhost:8098/graphiql"

wait
