cd /datashare/keystonebot
nohup mvn -Dspring.profiles.active=$1 exec:java > /datashare/logs/feed-$1.log &
tail -f /datashare/logs/feed-$1.log
