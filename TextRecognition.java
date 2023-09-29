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
        String profileName = "default";
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profileName);

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
            System.out.println("Polling file (printing line console): " + imageIndex);
            outputLines.add("Polling file: " + imageIndex);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(imageIndex).build();
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                    .image(Image.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);

            boolean hasCarAndText = false;
            StringBuilder lineText = new StringBuilder(imageIndex + ": ");

            for (TextDetection text : detectTextResponse.textDetections()) {
                if (text.type().equals("LINE")) {
                    hasCarAndText = true;
                    lineText.append(text.detectedText()).append(" ");
                }
            }

            if (hasCarAndText) {
                outputLines.add(lineText.toString());
            }

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

        rekognition.close();
        s3.close();
        sqs.close();
    }
}
