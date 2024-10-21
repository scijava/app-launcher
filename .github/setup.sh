#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/main/ci-setup-github-actions.sh
sh ci-setup-github-actions.sh

if [ "$(uname)" != Linux ]
then
  echo "No deploy -- non-Linux build"
  echo "NO_DEPLOY=1" >> $GITHUB_ENV
fi
