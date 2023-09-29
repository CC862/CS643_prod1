## reminder every session edit creds file ##
1. mkdir ~/.aws
2. nano ~/.aws/credentials
3. insert current new session creds
  ```
     [default]
     aws_access_key_id = YOUR_ACCESS_KEY
     aws_secret_access_key = YOUR_SECRET_KEY
  ```
5. compile project:
  * mvn compile

6. run program:
  ```
    mvn exec:java -Dexec.mainClass="cmc.app.CarDetectionApp"
  ```
7. Extract the Maven archive you downloaded:
  ```
    tar -xzvf apache-maven-3.9.4-bin.tar.gz  # for tar.gz file
    # or 
    unzip apache-maven-3.9.4-bin.zip  # for zip file
  ```
8. Configuring Environment Variables:
  * Open your .bash_profile, .bashrc, or .zshrc file (whichever is applicable) in a text editor.
  * Add the following lines to the file (adjust the paths if necessary):
  ```
    export M2_HOME=/usr/local/apache-maven
    export PATH=$M2_HOME/bin:$PATH
  ```
  * Save and close the file.
  * Apply the changes by running:
  ```
    source ~/.bash_profile
    # or
    source ~/.bashrc
    # or
    source ~/.zshrc
  ```
  * verify maven install:
  ```
   mvn -v
  ```
