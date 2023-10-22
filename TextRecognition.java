package cmc.app;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.io.InputStream;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TextRecognition {

    public static void main(String[] args) throws IOException {
        // creds
        String profileName = "default";
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profileName);

        // Initialize AWS services clients: S3, Rekognition, and SQS with the specified region and credentials
        Region region = Region.US_EAST_1;

        S3Client s3 = S3Client.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        RekognitionClient rekognition = RekognitionClient.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        SqsClient sqs = SqsClient.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        String bucketName = "njit-cs-643";
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/261847612621/CMCImageQueue";

        // Get the current date and time
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDateTime = now.format(formatter);

        // Create the output file name with the date
        String outputFilePath = "output/textrec_output_" + formattedDateTime + ".txt";

        // Create output file
        File outputFile = new File(outputFilePath);
        try {
            boolean fileCreated = outputFile.createNewFile();
            if (!fileCreated) {
                System.out.println("File already exists or failed to be created.");
            }
        } catch (IOException e) {
            e.printStackTrace();  // Handle exception
        }

        List<String> outputLines = new ArrayList<>(); // List to store output lines

        // Add date and time stamp as the first line in the file
        outputLines.add("Date and Time Stamp: " + formattedDateTime);


        while (true) {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(20)
                    .build();

            ReceiveMessageResponse receiveMessageResponse = sqs.receiveMessage(receiveMessageRequest);

            if (receiveMessageResponse.messages().isEmpty()) {
                // No new messages, continue polling or implement back-off strategy
                continue;
            }

            Message message = receiveMessageResponse.messages().get(0);
            String imageIndex = message.body();

            if (imageIndex.equals("-1")) {
                // Exit loop if termination message is received
                break;
            }

            // Add polling information to the output
            String pollingInfo = "Polling file: " + imageIndex;
            System.out.println("now polling file: " + imageIndex);
            outputLines.add(pollingInfo);

            // Capture and add the additional information
            String messageId = message.messageId();
            outputLines.add("ID: " + messageId);

            int messageSize = message.body().length();
            outputLines.add("Size: " + messageSize + " bytes");

            String md5MessageBody = message.md5OfBody();
            outputLines.add("MD5 of message body: " + md5MessageBody);
            outputLines.add(" ");

            // Declare the imageBytes variable outside the try-with-resources block
            SdkBytes imageBytes = null;
           // DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);


           // Fetch the image from S3
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(imageIndex + ".jpg")
            .build();

            ResponseInputStream<GetObjectResponse> responseStream = s3.getObject(getObjectRequest);
            try (InputStream objectData = responseStream) {
                imageBytes = SdkBytes.fromInputStream(objectData);
            }

            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
            .image(Image.builder().bytes(imageBytes).build())
            .build();
    
            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);


            // Process and store the result
            for (TextDetection textDetection : detectTextResponse.textDetections()) {
                String detectedText = textDetection.detectedText();
                System.out.println("Detected: " + detectedText);
                outputLines.add("Detected: " + detectedText);
            }

            // Delete the message from SQS after processing
            String receiptHandle = message.receiptHandle();
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqs.deleteMessage(deleteMessageRequest);
        }

        // Write the accumulated lines to the file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath))) {
            for (String line : outputLines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error writing to file: " + e.getMessage());
        }

        // Close all AWS services clients
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
