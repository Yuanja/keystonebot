cd /datashare/gwbot
mvn compile
nohup mvn -Dspring.profiles.active=gw-prod exec:java > gruenbergwatches.log &
tail -f gruenbergwatches.log
