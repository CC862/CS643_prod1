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

import java.util.List;

public class CarDetection {

    public static void main(String[] args) {
        // Setting up AWS credentials
        String profileName = "default";
        ProfileCredentialsProvider credentialsProvider;
        credentialsProvider = ProfileCredentialsProvider.create(profileName);

        // Setting up region for AWS services
        Region region = Region.US_EAST_1;

        // Creating clients for S3, Rekognition, and SQS
        S3Client s3 = S3Client.builder().region(region).credentialsProvider(credentialsProvider).build();
        RekognitionClient rekognition = RekognitionClient.builder().region(region).credentialsProvider(credentialsProvider).build();
        SqsClient sqs = SqsClient.builder().region(region).credentialsProvider(credentialsProvider).build();

        // Setting up bucket name and SQS URL
        String bucketName = "njit-cs-643";
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/261847612621/CMCImageQueue";

        // Loop to process 10 images
        for (int i = 1; i <= 10; i++) {
            String imageName = i + ".jpg";

            // Getting image from S3
            GetObjectRequest.Builder getObjectRequestBuilder = GetObjectRequest.builder();
            GetObjectRequest getObjectRequest = getObjectRequestBuilder.bucket(bucketName).key(imageName).build();
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            // Preparing request for Rekognition
           Image.Builder imageBuilder = Image.builder();
           Image img = imageBuilder.bytes(SdkBytes.fromByteArray(imageBytes)).build();
           DetectLabelsRequest.Builder detectLabelsRequestBuilder = DetectLabelsRequest.builder();
           DetectLabelsRequest detectLabelsRequest = detectLabelsRequestBuilder.image(img).maxLabels(10).build();

            // Detecting labels using Rekognition
            DetectLabelsResponse detectLabelsResponse = rekognition.detectLabels(detectLabelsRequest);
            List<Label> detectedLabels = detectLabelsResponse.labels();

            // Checking if 'Car' label exists and has a confidence over 90%
            for (int j = 0; j < detectedLabels.size(); j++) {
                Label label = detectedLabels.get(j);
                if ("Car".equalsIgnoreCase(label.name()) && label.confidence() > 90.0) {
                    // Sending the image name to SQS if the above condition is true
                    SendMessageRequest sendMessageRequest = new SendMessageRequest();
                    sendMessageRequest = sendMessageRequest.toBuilder().queueUrl(sqsQueueUrl).messageBody(imageName).build();
                    sqs.sendMessage(sendMessageRequest);
                    System.out.println("Sent image name " + imageName + " to SQS.");
                }
            }
        }

        // Sending termination message
        SendMessageRequest.Builder terminationMsgBuilder = SendMessageRequest.builder();
        SendMessageRequest terminationMsg = terminationMsgBuilder.queueUrl(sqsQueueUrl).messageBody("-1").build();
        sqs.sendMessage(terminationMsg);
        System.out.println("Sent termination message to SQS.");

        // Closing all AWS clients
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
