#!/bin/bash

  echo "======================================"
  echo "Kill FeedSync processes on $1..."
  echo "======================================"
  ./ssh-"$1".sh "pkill -f FeedSync; sleep 10"
  echo ""

