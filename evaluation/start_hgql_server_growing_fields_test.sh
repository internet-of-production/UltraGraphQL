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
log "Starting UGQL server for the Growing Fields Test"

log "Starting UGQL 1.1.0 endpoint in the background"
java -jar ./ultragraphql-1.1.0-exe.jar --config "configs/growing_fields/config_hgql_2.0.0.json" >> logs/growing_fields/ugql_1.1.0.log &
log "Interface running at http://localhost:8092/graphql"

log "Starting UGQL 1.1.0 Standalone endpoint in the background"
java -jar ./ultragraphql-1.1.0-exe.jar --config "configs/growing_fields/config_hgql_2.0.0_standalone.json" >> logs/growing_fields/ugql_1.1.0_standalone.log &
log "Interface running at http://localhost:8093/graphql"

#log "Starting HGQL 1.0.3 endpoint in the background"
#java -jar ./hypergraphql-1.0.3-exe.jar --config "configs/growing_fields/config_hgql_1.0.3.json" >> logs/growing_fields/hgql_1.0.3.log &
#log "Interface running at http://localhost:8091/graphql"

wait
