## reminder every session edit creds file ##
1. mkdir ~/.aws
2. nano ~/.aws/credentials
3. insert current new session creds
   ```
     [default]
     aws_access_key_id = YOUR_ACCESS_KEY
     aws_secret_access_key = YOUR_SECRET_KEY

5. compile project:
  * mvn compile

6. run program:
  * mvn exec:java -Dexec.mainClass="cmc.app.CarDetectionApp"
