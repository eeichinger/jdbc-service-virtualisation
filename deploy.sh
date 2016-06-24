#!/usr/bin/env bash
if [ "$TRAVIS_BRANCH" = 'master' ] && [ "$TRAVIS_PULL_REQUEST" == 'false' ]; then
    openssl aes-256-cbc -K $encrypted_11690a8d58e8_key -iv $encrypted_11690a8d58e8_iv -in gpg.pubring.gpg.enc -out gpg.pubring.gpg -d
    openssl aes-256-cbc -K $encrypted_11690a8d58e8_key -iv $encrypted_11690a8d58e8_iv -in gpg.secring.gpg.enc -out gpg.secring.gpg -d

    mvn deploy -B -Psign --settings settings.xml
fi
