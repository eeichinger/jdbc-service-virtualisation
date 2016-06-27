#!/usr/bin/env bash

# configure identity
git config user.email "eeichinger+travisci@gmail.com"
git config user.name "travis-ci-eeichinger"

# add ssh url to project
git remote add site git@github.com:eeichinger/jdbc-service-virtualisation.git

# stash away any changes
git add -A :/
git stash

# fetch gh-pages branch
git fetch site gh-pages:refs/remotes/site/gh-pages
git checkout -b gh-pages site/gh-pages

# update docs
git rm -rf apidocs
cp -R ./target/apidocs .
git rm -rf japicmp
cp -R ./target/japicmp .

# push everything back
git add -A
git commit -m "update build reports"
git push site gh-pages:gh-pages
