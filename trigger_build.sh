#!/usr/bin/env bash

# script to manually trigger the build on travis, see https://docs.travis-ci.com/user/triggering-builds

body='{
"request": {
  "branch":"master"
}}'

curl -s -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json" \
  -H "Travis-API-Version: 3" \
  -H "Authorization: token $1" \
  -d "$body" \
  https://api.travis-ci.org/repo/eeichinger/jdbc-service-virtualisation/requests
