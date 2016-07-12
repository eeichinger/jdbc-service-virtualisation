#!/usr/bin/env bash

# configure identity
git config user.email "eeichinger+travisci@gmail.com"
git config user.name "travis-ci-eeichinger"

# add ssh url to project
git remote add site git@github.com:eeichinger/jdbc-service-virtualisation.git

# stash away any changes
# TODO: doesn't work?
# /usr/lib/git-core/git-stash: 186: /usr/lib/git-core/git-stash: cannot create /home/travis/build/eeichinger/jdbc-service-virtualisation/.git/logs/refs/stash: Directory nonexistent
#git add -A :/
#git stash

git reset --hard

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
