#!/bin/bash

if [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_BRANCH" == "master" ]; then
  echo "*************************************"
  echo "            Publishing               "
  echo "*************************************"
  sbt publish
fi
