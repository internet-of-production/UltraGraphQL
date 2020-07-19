#!/usr/bin/env bash""

trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT
HOST_hgql_1="http://localhost:8098/graphql"

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
log "Starting HGQL server for the DBpedia Test"

log "Starting HGQL 2.0.0 endpoint in the background"
java -jar ./hypergraphql-2.0.0-exe.jar --config "configs/dbpedia/config_hgql_2.0.0.json" >> logs/dbpedia/hgql_2.0.0.log &
log "Interface running at http://localhost:8092/graphql"


wait

