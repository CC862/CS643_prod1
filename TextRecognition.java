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
        String outputFilePath = "/path/to/output.txt"; // Update with the actual path

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

            GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(imageIndex).build();
            byte[] imageBytes = s3.getObjectAsBytes(getObjectRequest).asByteArray();

            DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                    .image(Image.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            DetectTextResponse detectTextResponse = rekognition.detectText(detectTextRequest);

            for (TextDetection text : detectTextResponse.textDetections()) {
                if (text.type().equals("LINE")) {
                    writer.write(imageIndex + ": " + text.detectedText());
                    writer.newLine();
                }
            }

            String receiptHandle = message.receiptHandle();
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqs.deleteMessage(deleteMessageRequest);
        }

        writer.close();
        rekognition.close();
        s3.close();
        sqs.close();
    }
}
