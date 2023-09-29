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

        // Create the output file name with the dtate 
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

        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));

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
            System.out.println("Polling file: " + imageIndex); 

            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(imageIndex).build();
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                    .image(Image.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);
            //System.out.println(detectTextResponse);  // for debug

            for (TextDetection text : detectTextResponse.textDetections()) {
                if (text.type().equals("LINE")) {
                    writer.write(imageIndex + ": " + text.detectedText());
                    writer.newLine();
                    //writer.flush();  // flush the writer
                }
            }

            // Flush and close the writer after each message
            //writer.flush();
            //writer.close();

            String receiptHandle = message.receiptHandle();
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqs.deleteMessage(deleteMessageRequest);
        }

        //writer = new BufferedWriter(new FileWriter(outputFilePath, true));
        if (writer != null) {
            writer.close(); // Close the writer after the loop
        }

        //writer.close();
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
