#!/usr/bin/env bash

env | grep "TRAVIS_"

IS_RELEASE=true
IS_MASTER=true
IS_PR=true
IS_RELEASE_BUILD=true

[[ "$TRAVIS_TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.RELEASE$ ]] || IS_RELEASE=false
[ "$TRAVIS_BRANCH" = 'master' ] || IS_MASTER=false
[ "$TRAVIS_PULL_REQUEST" = 'true' ] || IS_PR=false

echo IS_MASTER=$IS_MASTER
echo IS_RELEASE=$IS_RELEASE

[[ "$IS_RELEASE" = 'true' || "$IS_MASTER" = 'true' ]] || IS_RELEASE_BUILD=false

echo IS_RELEASE_BUILD=$IS_RELEASE_BUILD

[ "$IS_PR" = 'false' ] || IS_RELEASE_BUILD=false

if [[ "$IS_RELEASE_BUILD" = 'true' ]]; then
    echo release build

    openssl aes-256-cbc -K $encrypted_11690a8d58e8_key -iv $encrypted_11690a8d58e8_iv -in gpg.secrets.tar.enc -out gpg.secrets.tar -d
    tar xvf gpg.secrets.tar

    mvn deploy -DskipTests -B -Psign --settings settings.xml
else
    echo default build

    mvn install -B
fi
