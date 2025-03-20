cd /datashare/gwbot
mvn compile
nohup mvn -Dspring.profiles.active=chronos24-prod exec:java > chronos24.log &
tail -f chronos24.log
