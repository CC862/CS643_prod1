package cmc.app;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class TextRecognition {

    public static void main(String[] args) {
        // Creating variable for profile name
        String profile = "default";
        ProfileCredentialsProvider provider = ProfileCredentialsProvider.create(profile);

        // Setting up AWS services 
        Region myRegion = Region.US_EAST_1;
        S3Client s3 = S3Client.builder().region(myRegion).credentialsProvider(provider).build();
        RekognitionClient rekognition = RekognitionClient.builder().region(myRegion).credentialsProvider(provider).build();
        SqsClient sqs = SqsClient.builder().region(myRegion).credentialsProvider(provider).build();

        // Variables for bucket and queue
        String bucket = "njit-cs-643";
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/261847612621/CMCImageQueue";

        // Getting the current time
        LocalDateTime timeNow = LocalDateTime.now();
        DateTimeFormatter formatTime = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String timeString = timeNow.format(formatTime);

        // Making a file with the time in its name
        String filePath = "output/textrec_output_" + timeString + ".txt";
        File file = new File(filePath);
        
        try {
            if (file.createNewFile()) {
                System.out.println("File created.");
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.out.println("An error occurred when creating the file.");
            e.printStackTrace();
        }

        ArrayList<String> linesForFile = new ArrayList<>();
        linesForFile.add("Date and Time: " + timeString);

        while (true) {
            ReceiveMessageRequest getMessage = ReceiveMessageRequest.builder().queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(20).build();
            ReceiveMessageResponse messageResponse = sqs.receiveMessage(getMessage);

            if (messageResponse.messages().isEmpty()) {
                continue;  // Just skip if no new messages
            }

            Message myMessage = messageResponse.messages().get(0);
            String imageNum = myMessage.body();

            if (imageNum.equals("-1")) {
                break;  // Stop the loop
            }

            System.out.println("Checking file: "+ imageNum);
            linesForFile.add("Checking file: " + imageNum);

            String messageId = myMessage.messageId();
            linesForFile.add("ID: " + messageId);
            linesForFile.add("Size: " + myMessage.body().length() + " bytes");
            linesForFile.add("MD5: " + myMessage.md5OfBody());
            linesForFile.add(" ");

            // Deleting the processed message
            String receipt = myMessage.receiptHandle();
            DeleteMessageRequest deleteIt = DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receipt).build();
            sqs.deleteMessage(deleteIt);
        }

        // Save everything to the file
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
            for (String line : linesForFile) {
                writer.write(line);
                writer.newLine();
            }
            writer.close();
        } catch (IOException e) {
            System.out.println("An error occurred when writing to the file.");
            e.printStackTrace();
        }

        rekognition.close();
        s3.close();
        sqs.close();
    }
}
