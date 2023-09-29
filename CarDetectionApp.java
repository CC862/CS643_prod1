package cmc.app;

// Import necessary AWS SDK and other classes
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

public class CarDetectionApp {

    public static void main(String[] args) {
        // pointing to AWS profile to use, from creds file that needs to be updated every session
        String profileName = "default"; 
        ProfileCredentialsProvider credentialsProvider = ProfileCredentialsProvider.create(profileName);

        // AWS region
        Region region = Region.US_EAST_1;

        // clients for S3, Rekognition, and SQS services
        S3Client s3 = S3Client.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        RekognitionClient rekognition = RekognitionClient.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        SqsClient sqs = SqsClient.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        // point S3 bucket name and SQS queue URL, the bucket already in place
        // queue is mine i made up in aws 
        String bucketName = "njit-cs-643";
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/261847612621/CMCImageQueue";

        // Loop through images 1.jpg to 10
        for (int i = 1; i <= 10; i++) {
            String key = i + ".jpg";
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();

            // pulling image
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            // Build the request to detect labels in the image
            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                    .image(Image.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .maxLabels(10)
                    .build();

            // Send the request to Rekognition / get the response
            DetectLabelsResponse detectLabelsResponse = rekognition.detectLabels(detectLabelsRequest);

            // Check the detected labels for a car 
            for (Label label : detectLabelsResponse.labels()) {
                if (label.name().equalsIgnoreCase("Car") && label.confidence() > 90) {
                    // message compile SQS
                    SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                            .queueUrl(sqsQueueUrl)
                            .messageBody(key)
                            .build();
                    // Send the message to SQS
                    sqs.sendMessage(sendMessageRequest);
                    System.out.println("Sent image index " + key + " to SQS.");
                }
            }
        }

        // Close 
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
