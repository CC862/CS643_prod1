package cmc.app;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;


public class CarDetectionApp {

    public static void main(String[] args) {
        Region region = Region.US_EAST_1;
        S3Client s3 = S3Client.builder().region(region).build();
        RekognitionClient rekognition = RekognitionClient.builder().region(region).build();
        SqsClient sqs = SqsClient.builder().region(region).build();
        String bucketName = "njit-cs-643";
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/261847612621/CMCImageQueue";

        for (int i = 1; i <= 10; i++) {
            String key = i + ".jpg";
            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build();
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();
            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                    .image(Image.builder()
                            .bytes(RequestBody.fromBytes(imageBytes))
                            .build())
                    .maxLabels(10)
                    .build();

            DetectLabelsResponse detectLabelsResponse = rekognition.detectLabels(detectLabelsRequest);

            for (Label label : detectLabelsResponse.labels()) {
                if (label.name().equalsIgnoreCase("Car") && label.confidence() > 90) {
                    SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                            .queueUrl(sqsQueueUrl)
                            .messageBody(key)
                            .build();
                    sqs.sendMessage(sendMessageRequest);
                    System.out.println("Sent image index " + key + " to SQS.");
                }
            }
        }

        rekognition.close();
        s3.close();
        sqs.close();
    }
}
