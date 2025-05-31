#!/bin/bash

./status-keystone.sh
./recompile-keystone.sh
./kill-keystone.sh
./status-keystone.sh
./start-keystone.sh
./status-keystone.sh
