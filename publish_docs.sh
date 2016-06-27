#rm -rf target/gh-pages
#mkdir -p target/gh-pages
#cd target/gh-pages
#git init
#git remote add origin https://github.com/eeichinger/jdbc-service-virtualisation.git
#git pull origin gh-pages

git remote add site https://travis-ci-eeichinger:$GITHUB_TRAVISCI_PASSWORD@github.com/eeichinger/jdbc-service-virtualisation.git  > /dev/null
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
git push site gh-pages:gh-pages > /dev/null

# TODO: solve git authentication
