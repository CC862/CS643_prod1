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

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String formattedDateTime = now.format(formatter);
        String outputFilePath = "output/textrec_output_" + formattedDateTime + ".txt";

        File outputFile = new File(outputFilePath);
        try {
            boolean fileCreated = outputFile.createNewFile();
            if (!fileCreated) {
                System.out.println("File already exists or failed to be created.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        List<String> outputLines = new ArrayList<>();
        outputLines.add("Date and Time Stamp: " + formattedDateTime);

        while (true) {
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(20)
                    .build();

            ReceiveMessageResponse receiveMessageResponse = sqs.receiveMessage(receiveMessageRequest);

            if (receiveMessageResponse.messages().isEmpty()) {
                continue;
            }

            Message message = receiveMessageResponse.messages().get(0);
            String imageIndex = message.body();

            if (imageIndex.equals("-1")) {
                break;
            }

            System.out.println("now polling file: "+ imageIndex);
            outputLines.add("Polling file: " + imageIndex);

            // Capture and add the additional information
            String messageId = message.messageId();
            outputLines.add("ID: " + messageId);

            int messageSize = message.body().length();
            outputLines.add("Size: " + messageSize + " bytes");

            String md5MessageBody = message.md5OfBody();
            outputLines.add("MD5 of message body: " + md5MessageBody);
            outputLines.add(" ");

            // Fetch the image bytes from S3
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(imageIndex)
                .build();
            byte[] bytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();
            
            // Detect text using Rekognition
            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                .image(Image.builder()
                    .bytes(SdkBytes.fromByteArray(bytes))
                    .build())
                .build();
            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);

            // Add detected text to output
            for (TextDetection textDetection : detectTextResponse.textDetections()) {
                outputLines.add("Detected Text: " + textDetection.detectedText());
                outputLines.add("Confidence: " + textDetection.confidence().toString());
            }

            String receiptHandle = message.receiptHandle();
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqs.deleteMessage(deleteMessageRequest);
        }

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
