#!/bin/bash

  echo "======================================"
  echo "Kill FeedSync processes on $1..."
  echo "======================================"
  ./ssh-"$1".sh "pkill -9 java; sleep 10"
  echo ""

