#!/usr/bin/env bash

env | grep "TRAVIS_"

if [[ \("$TRAVIS_BRANCH" == 'master' || "$TRAVIS_TAG" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.RELEASE$ \) &&  \("$TRAVIS_PULL_REQUEST" == 'false'\) ]]
then
    openssl aes-256-cbc -K $encrypted_11690a8d58e8_key -iv $encrypted_11690a8d58e8_iv -in gpg.secrets.tar.enc -out gpg.secrets.tar -d
    tar xvf gpg.secrets.tar

    mvn deploy -DskipTests -B -Psign --settings settings.xml
else
    mvn install -B
fi
