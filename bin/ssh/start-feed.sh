#!/bin/bash

echo "======================================"
echo "Start FeedSync processes on $1"
echo "======================================"
./ssh-$1.sh "cd /datashare/keystone;bin/start-feed.sh $1"
echo ""
