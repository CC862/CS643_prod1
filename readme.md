## reminder every session edit creds file ##
1. mkdir ~/.aws
2. nano ~/.aws/credentials
3. insert current new session creds
  ```
     [default]
     aws_access_key_id = YOUR_ACCESS_KEY
     aws_secret_access_key = YOUR_SECRET_KEY
  ```

  ## compile and run program CarDetection.java ##
1. compile project:
  * mvn compile

2. run program:
  ```
    mvn exec:java -P car-detection 
  ```

## compile and run program TextRecognition.java ##
1. compile project:
  * mvn compile

2. run program:
  ```
    mvn exec:java -P text-recognition
  ```

 ## Maven install process and verify  ##
1. Extract the Maven archive you downloaded:
  ```
    tar -xzvf apache-maven-3.9.4-bin.tar.gz  # for tar.gz file
    # or 
    unzip apache-maven-3.9.4-bin.zip  # for zip file
  ```
  * sudo mv apache-maven-3.9.4 /usr/local/apache-maven

2. Configuring Environment Variables:
  * Open your .bash_profile in a text editor.
  * Add the following lines to the file (adjust the paths if necessary):
  ```
    export M2_HOME=/usr/local/apache-maven
    export PATH=$M2_HOME/bin:$PATH
  ```
  * Save and close the file.
  * Apply the changes by running:
  ```
    source ~/.bash_profile
   
  ```
  * verify maven install:
  ```
   mvn -v
  ```
