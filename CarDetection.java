package cmc.app;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class CarDetection {

    public static void main(String[] args) {
        //creds
        String profileName = "default";
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profileName);

        Region region = Region.US_EAST_1;

        // Initialize AWS services clients: S3, Rekognition, and SQS with the specified region and credentials
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

        for (int i = 1; i <= 10; i++) {
            String key = i + ".jpg";
            // Create a req to get the image from the specified S3 bucket, provided from project description
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();

            // Retrieve the image bytes from S3
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            // Create a request to detect labels in the image using Rekognition
            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                    .image(Image.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .maxLabels(10)
                    .build();

            // label detect
            DetectLabelsResponse detectLabelsResponse = rekognition.detectLabels(detectLabelsRequest);

            for (Label label : detectLabelsResponse.labels()) {
                // if a detected label is "Car" with confidence above 90%
                if (label.name().equalsIgnoreCase("Car") && label.confidence() > 90) {
                // message with the image index and send it to the specified SQS queue, I had to make my own queue 
                    SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                            .queueUrl(sqsQueueUrl)
                            .messageBody(key)
                            .build();
                    sqs.sendMessage(sendMessageRequest);
                    System.out.println("Sent image index " + key + " to SQS.");
                }
            }
        }
        // Send termination message (-1) 
        SendMessageRequest sendTerminationMsgRequest = SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody("-1")
                .build();
        sqs.sendMessage(sendTerminationMsgRequest);
        System.out.println("Sent termination message to SQS.");
        
        // Close all AWS services clients 
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
