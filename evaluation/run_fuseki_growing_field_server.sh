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


log "Starting Fuseki server in the background"
./apache-jena-fuseki-3.7.0/fuseki-server --config=fuseki_config_growing_field.ttl > fuseki.log &
log "Interface running at ${HOST}"

sleep 8s

#${FBIN}/s-put ${HOST}/dataset/data default data/raw/persons_and_cars.ttl

#${FBIN}/s-put ${HOST}/foaf/data default data/raw/foaf_person.ttl
log "Loaded Schema.org encoded data into named graph default"

wait

