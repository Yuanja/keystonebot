cd /datashare/gwbot
mvn compile
nohup mvn -Dspring.profiles.active=gwebay-prod exec:java > gwebay.log &
tail -f gwebay.log
