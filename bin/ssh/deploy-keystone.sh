#!/bin/bash

./status-keystone.sh
./recompile-keystone.sh
./kill-feedsync.sh keystone-prod
./status-keystone.sh
./start-keystone.sh
./status-keystone.sh
