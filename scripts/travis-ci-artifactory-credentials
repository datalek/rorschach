#!/bin/bash
[ -d ~/.bintray/ ] || mkdir ~/.bintray/
ARTIFACTORY_FILE=$HOME/.bintray/.artifactory
cat <<EOF >$ARTIFACTORY_FILE
realm = Artifactory Realm
host = oss.jfrog.org
user = $BINTRAY_USER
password = $BINTRAY_PASSWORD
EOF