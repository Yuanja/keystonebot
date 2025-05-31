#!/bin/bash

echo "======================================"
echo "Start FeedSync processes on keystone-prod"
echo "======================================"
./ssh-keystone-prod.sh "cd /datashare/keystonebot;bin/start-feed.sh keystone-prod"
echo ""
