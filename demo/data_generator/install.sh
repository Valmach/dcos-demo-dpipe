#!/usr/bin/env bash

set -o errexit
set -o errtrace
set -o nounset
set -o pipefail

if ! dcos --version 2>/dev/null; then
  echo "Install a dcos cli!"
  exit 1
fi

# setup kafka
echo "Installing data_generator service"
if ! dcos node > /dev/null 2<&1; then
    dcos auth login
fi

# generate service-generator.json
brokers=$(dcos kafka connection|jq -r '.dns|join(",")')
if [ -z "$brokers" ] ; then
    echo "ERROR : no brokers available to connect to.  Deploy kafka or broker services first."
    exit 1
fi

cat ./service-generator.json| \
jq -r --arg brokers $brokers \
 '.args=["-broker", $brokers]|.' > ./.service-generator.json

dcos marathon app add ./.service-generator.json

echo "--> wait 2 minutes"
sleep 120

# verify deployment status
dcos marathon app list
