#!/usr/bin/env bash

trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT
export FUSEKI_HOME="${PWD}/apache-jena-fuseki-3.7.0"
FBIN="./apache-jena-fuseki-3.7.0/bin"
HOST="http://localhost:3030"

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

query1=$(cat query_test/sparql_query_1.sparql)
query2=$(cat query_test/sparql_query_2.sparql)
query3=$(cat query_test/sparql_query_3.sparql)


ROUNDS=1000
Query(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    (${FBIN}/s-query --service ${HOST}/dataset --output=csv "$QUERY") > query_test/result_sparql_query_${NR}.log
    done
    DIFF=$(echo "$(date +%s.%N) - $START" | bc)
    AVG=$(echo "scale=4; $DIFF/$ROUNDS" | bc)
    log "Execution time total: ${DIFF} seconds"
    log "Execution time AVG: ${AVG} seconds\n\n"
}

log "Each query will be queried ${ROUNDS} times to estimate the average query execution time."
log "Starting Query 1a:\n"
Query "1" "$query1"

log "Starting Query 2a"
Query "2" "$query2"

log "Starting Query 3a"
Query "3" "$query3"

query1b="query=PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0A%0ASELECT+%3Fmodel+%3Fperson%0AWHERE+%7B%0A++%3Fcar+a+ex%3Acar.%0A++OPTIONAL%7B%0A++%09%3Fcar+ex%3Amodel+%3Fmodel.%0A++%7D%0A++OPTIONAL%7B%0A++%09%3Fcar+ex%3AownedBy+%3Fperson.%0A++%7D%0A%7D"
query2b=query="PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0A%0ASELECT+%3Fperson+%3Fname+%3Fsurname+%3Fage%0AWHERE+%7B%0A++%7BSELECT+%3Fperson%0A++WHERE%7B%0A++++%3Fperson+a+ex%3APerson.%0A++%7D%0A++++LIMIT+100%7D%0A++OPTIONAL%7B%0A++%09%3Fperson+ex%3Aname+%3Fname.%0A++%7D%0A++OPTIONAL%7B%0A++%09%3Fperson+ex%3Asurname+%3Fsurname.%0A++%7D%0A++OPTIONAL%7B%0A++%09%3Fperson+ex%3Aage+%3Fage.%0A++%7D%0A%7D"
query3b="query=PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0A%0ASELECT+%3Frel1+%3Frel2+%3Frel3%0AWHERE+%7B%0A++ex%3Aperson_999+a+ex%3APerson.%0A++OPTIONAL%7B%0A++%09ex%3Aperson_999+ex%3ArelatedWith+%3Frel1.%0A++++OPTIONAL%7B%0A++%09%09%3Frel1+ex%3ArelatedWith+%3Frel2.%0A++++++%09OPTIONAL%7B%0A++%09%09%09%3Frel2+ex%3ArelatedWith+%3Frel3.%0A++%09%09%7D%0A++%09%7D%0A++%7D%0A%7D"


Query2(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    curl -s --location --request POST 'http://localhost:3030/dataset/query' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-raw ${QUERY} > query_test/result_sparql_query_${NR}.log
    done
    DIFF=$(echo "$(date +%s.%N) - $START" | bc)
    AVG=$(echo "scale=4; $DIFF/$ROUNDS" | bc)
    log "Execution time total: ${DIFF} seconds"
    log "Execution time AVG: ${AVG} seconds\n\n"
}
log "Starting Query 1b:\n"
Query2 "1b" "$query1b"

log "Starting Query 2b"
Query2 "2b" "$query2b"

log "Starting Query 3b"
Query2 "3b" "$query3b"
