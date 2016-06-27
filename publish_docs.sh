#!/usr/bin/env bash
#rm -rf target/gh-pages
#mkdir -p target/gh-pages
#cd target/gh-pages
#git init
#git remote add origin https://github.com/eeichinger/jdbc-service-virtualisation.git
#git pull origin gh-pages

# stash away any changes
git add -A :/
git stash


git remote add site git@github.com:eeichinger/jdbc-service-virtualisation.git
git config user.email "eeichinger+travisci@gmail.com"
git config user.name "travis-ci-eeichinger"
git fetch site gh-pages:refs/remotes/site/gh-pages
git checkout -b gh-pages site/gh-pages
git rm -rf apidocs
cp -R ./target/apidocs .
git rm -rf japicmp
cp -R ./target/japicmp .
git add -A
git commit -m "update build reports"
git push site gh-pages:gh-pages
