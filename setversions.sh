#!/usr/bin/env bash
set -v > /dev/null

echo "TRAVIS_TAG: $TRAVIS_TAG"

if [[ "$TRAVIS_TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.RELEASE$ ]]; then
  mvn versions:set -DnewVersion=$TRAVIS_TAG
  echo -n "setting version $TRAVIS_TAG"
fi
