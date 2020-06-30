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

query1=$(cat multiple_service_test/query_1_sparql.sparql)
query2=$(cat multiple_service_test/query_2_sparql.sparql)
query3=$(cat multiple_service_test/query_3_sparql.sparql)


ROUNDS=1000
Query(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    (${FBIN}/s-query --service ${HOST}/ex_person --output=csv "$QUERY") > multiple_service_test/result_sparql_query_${NR}.log
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

query1b="query=PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0APREFIX+foaf%3A+%3Chttp%3A%2F%2Fxmlns.com%2Ffoaf%2F0.1%2F%3E%0ASELECT++%3Fen+%3Ffn%0AWHERE%7B+%0A++VALUES+%3Fperson+%7Bfoaf%3APerson+ex%3APerson%7D%0A++%7Bex%3Aperson_253+a+%3Fperson%3B%0A+++++++++++++++++ex%3Aname+%3Fen.%7D%0A++UNION%7B%0A%09SERVICE+%3Chttp%3A%2F%2Flocalhost%3A3030%2Ffoaf_person%3E%7B%0A+%09%09foaf%3Aperson_253+a+%3Fperson%3B%0A++%09%09%09%09%09%09foaf%3Aname+%3Ffn%0A%09%7D%0A++%7D%0A%7D%0A%0A"
query2b="query=PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0APREFIX+foaf%3A+%3Chttp%3A%2F%2Fxmlns.com%2Ffoaf%2F0.1%2F%3E%0ASELECT++%3Fperson+%3Fmodel%0AWHERE%7B+%0A++%7B%3Fperson+a+ex%3APerson%3B%0A+++++++++++ex%3Adrives+%3Fc.%0A++%7D%0A++UNION%7B%0A++++SERVICE+%3Chttp%3A%2F%2Flocalhost%3A3030%2Ffoaf_person%3E%7B%0A++++++%3Fperson+a+foaf%3APerson%3B%0A++++++++++++++ex%3Adrives+%3Fc.%0A++++%7D%0A++%7D%0A++%3Fc+ex%3Amodel+%3Fmodel.%0A%7D"
query3b="query=PREFIX+ex%3A+%3Chttp%3A%2F%2Fexample.org%2F%3E%0APREFIX+foaf%3A+%3Chttp%3A%2F%2Fxmlns.com%2Ffoaf%2F0.1%2F%3E%0APREFIX+l%3A+%3Chttp%3A%2F%2Flocalhost%3A3030%2F%3E%0ASELECT++%3Ff1+%3Ff2+%3Ff3+%3Ff4%0AWHERE%7B+%0A++ex%3Aperson_934+ex%3ArelatedWith+%3Ff1.%0A++OPTIONAL%7B%0A++++SERVICE+l%3Afoaf_person%7B%0A++++%09%3Ff1+ex%3ArelatedWith+%3Ff2.%0A++++%7D%0A++++OPTIONAL%7B%0A+++++%3Ff2+ex%3ArelatedWith+%3Ff3.+%0A++++++OPTIONAL%7B%0A++++++++SERVICE+l%3Afoaf_person%7B%0A++++%09%3Ff3+ex%3ArelatedWith+%3Ff4.%0A++++%7D%0A++++++%7D%0A++++%7D%0A++%7D++%0A%7D+%0A"


Query2(){
    NR=$1
    QUERY=$2
    START=$(date +%s.%N)
    for (( i = 0; i <= $ROUNDS; i++ )) 
    do
    curl -s --location --request POST 'http://localhost:3030/ex_person/query' \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-raw ${QUERY} > multiple_service_test/result_sparql_query_${NR}.log
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
