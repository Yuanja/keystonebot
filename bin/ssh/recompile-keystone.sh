#!/bin/bash
./ssh-keystone-prod.sh "cd /datashare/keystonebot;git pull;mvn compile"
