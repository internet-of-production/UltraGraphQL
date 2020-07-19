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
java -jar ./hypergraphql-2.0.0-exe.jar --config "configs/mutations/config.json" >> logs/mutations/hgql.log &
log "Interface running at ${HOST}"

log "End the script or use the GraphiQL endpoint for custom queries at http://localhost:8098/graphiql"
sleep 10s

wait

