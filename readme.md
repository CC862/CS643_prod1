##reminder every session edit creds file##
mkdir ~/.aws
nano ~/.aws/credentials
- insert current new session creds
[default]
aws_access_key_id = YOUR_ACCESS_KEY
aws_secret_access_key = YOUR_SECRET_KEY

compile project:
- mvn compile

run program:
- mvn exec:java -Dexec.mainClass="cmc.app.CarDetectionApp"
