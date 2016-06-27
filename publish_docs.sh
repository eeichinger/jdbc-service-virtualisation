#rm -rf target/gh-pages
#mkdir -p target/gh-pages
#cd target/gh-pages
#git init
#git remote add origin https://github.com/eeichinger/jdbc-service-virtualisation.git
#git pull origin gh-pages

git config user.email "travis-ci@travis-ci.org"
git config user.name "Travis CI"
git fetch origin gh-pages:refs/remotes/origin/gh-pages
git checkout -b gh-pages origin/gh-pages
git rm -rf apidocs
cp -R .target/apidocs .
git rm -rf japicmp
cp -R .target/japicmp .
git add -A
git commit -m "update build reports"
git push origin gh-pages:gh-pages
