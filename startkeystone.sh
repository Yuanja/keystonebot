cd /datashare/keystonebot
mvn compile
nohup mvn -Dspring.profiles.active=keystone-prod exec:java > keystone.log &
tail -f keystone.log
