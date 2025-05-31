#!/bin/bash

  echo "======================================"
  echo "Kill FeedSync processes on ${PROFILE}..."
  echo "======================================"
  ./ssh-"$PROFILE".sh "pkill -f FeedSync; sleep 10"
  echo ""

