#!/usr/bin/env bash

env | grep "TRAVIS_"

IS_RELEASE=true
IS_MASTER=true
IS_PR=true

[[ "$TRAVIS_TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.RELEASE$ ]] || IS_RELEASE=false
[ "$TRAVIS_BRANCH" = 'master' ] || IS_MASTER=false
[ "$TRAVIS_PULL_REQUEST" = 'true' ] || IS_PR=false

[[ "$IS_MASTER" = 'true' || "$IS_RELEASE" = 'true' ]] || IS_MASTER=false
[ "$IS_PR" = 'false' ] || IS_MASTER=false

if [[ "$IS_MASTER" = 'true' ]]; then
    openssl aes-256-cbc -K $encrypted_11690a8d58e8_key -iv $encrypted_11690a8d58e8_iv -in gpg.secrets.tar.enc -out gpg.secrets.tar -d
    tar xvf gpg.secrets.tar

    mvn deploy -DskipTests -B -Psign --settings settings.xml
else
    mvn install -B
fi
