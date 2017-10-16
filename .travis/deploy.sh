#!/usr/bin/env bash
VERSION=$( echo "${TRAVIS_TAG##*-}" )
if [ "$TRAVIS_PULL_REQUEST" == 'false' ] && [ ! -z "$TRAVIS_TAG" ]
then
    echo "on a tag -> set pom.xml <version> to $VERSION"
    mvn --settings .travis/settings.xml org.codehaus.mojo:versions-maven-plugin:2.1:set -DnewVersion=$VERSION 1>/dev/null 2>/dev/null
    mvn deploy -P sign,build-extras --settings .travis/settings.xml
else
    mvn deploy -Dtravis=true -P !build-extras --settings .travis/settings.xml
fi
rc=$?
if [[ $rc -ne 0 ]] ; then
  echo 'Could deploy artifact to snaphot/release repository!'; exit $rc
fi