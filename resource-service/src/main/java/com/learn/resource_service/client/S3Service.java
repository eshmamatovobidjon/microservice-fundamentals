package com.learn.resource_service.client;

import com.learn.resource_service.dto.StorageDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class S3Service {
    private final S3Client s3Client;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public S3Service(@Value("${AWS_ACCESS_KEY}") String awsAccessKey,
                     @Value("${AWS_SECRET_KEY}") String awsSecretKey,
                     @Value("${AWS_REGION}") String awsRegion) {
        this.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials
                                .create(awsAccessKey, awsSecretKey)))
                .region(Region.of(awsRegion))
                .build();
    }

    private void verifyBucketAccess(String bucket) {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .maxKeys(1)
                    .build();

            s3Client.listObjectsV2(listRequest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot access S3 bucket: " + bucket + ". Please verify credentials and bucket permissions.", e);
        }
    }

    public String uploadMp3(byte[] mp3Data, String fileName, StorageDTO storage) {
        validateUploadParams(mp3Data, fileName);

        String targetBucket = storage.getBucket();
        String targetPath = storage.getPath();
        String key = getKey(fileName, targetPath);
        String s3Url = null;
        Exception lastException = null;
        verifyBucketAccess(targetBucket);

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(targetBucket)
                        .key(key)
                        .contentType("audio/mpeg")
                        .build();
                PutObjectResponse response = s3Client.putObject(putObjectRequest, RequestBody.fromBytes(mp3Data));
                if (response.eTag() == null || response.eTag().isEmpty()) {
                    throw new RuntimeException("Upload verification failed - missing ETag");
                }

                s3Url = String.format("https://%s.s3.amazonaws.com/%s", targetBucket, key);

                if (!fileExists(fileName, storage)) {
                    throw new RuntimeException("Upload verification failed - file not found after upload");
                }

                break;
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 upload attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Upload interrupted", ie);
                    }
                } else {
                    // Final attempt failed - clean up any partial upload
                    cleanupFailedUpload(fileName, storage);
                }
            }
        }

        if (s3Url == null) {
            throw new RuntimeException("Failed to upload file to S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
        }

        return s3Url;
    }

    private static String getKey(String fileName, String targetPath) {
        return (targetPath != null && !targetPath.isEmpty())
                ? targetPath.replaceAll("/+$", "") + "/" + fileName
                : fileName;
    }

    public void cleanupFailedUpload(String fileName, StorageDTO storage) {
        try {
            if (fileExists(fileName, storage)) {
                deleteFile(fileName, storage);
            }
        } catch (Exception e) {
            System.err.println("Failed to cleanup partial upload for file: " + fileName + " - " + e.getMessage());
        }
    }

    private void validateUploadParams(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Upload data cannot be null or empty");
        }
        validateFileName(fileName);
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }
        if (fileName.contains("..") || fileName.contains("\\") || fileName.length() > 255) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
    }


    public void deleteFile(String fileName, StorageDTO storage) {
        String bucket = storage.getBucket();
        String path = storage.getPath();
        String key = getKey(fileName, path);
        validateFileName(key);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();

                s3Client.deleteObject(deleteObjectRequest);

                if (fileExists(fileName, storage)) {
                    throw new RuntimeException("Delete verification failed - file still exists after deletion");
                }

                return;
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 delete attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Delete interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to delete file from S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + key, lastException);
    }

    public boolean fileExists(String fileName, StorageDTO storageDTO) {
        String bucket = storageDTO.getBucket();
        String path = storageDTO.getPath();
        String key = getKey(fileName, path);
        validateFileName(key);

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headObjectRequest);

            return response.contentLength() != null && response.contentLength() > 0;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            // Log the error but assume file exists to prevent accidental overwrites
            System.err.println("Error checking file existence for " + key + ": " + e.getMessage());
            return true; // Fail-safe
        }
    }

    public byte[] downloadFile(String fileName, StorageDTO storageDTO) {
        String bucket = storageDTO.getBucket();
        String path = storageDTO.getPath();
        String key = getKey(fileName, path);
        validateFileName(key);

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();

                byte[] fileContent = s3Client.getObject(getObjectRequest).readAllBytes();

                if (fileContent.length == 0) {
                    throw new RuntimeException("Downloaded file is empty: " + fileName);
                }

                return fileContent;

            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                throw new RuntimeException("File not found in S3: " + fileName, e);
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 download attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Download interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to download file from S3 after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
    }

    public void moveFile(String fileName, StorageDTO stagingStorage, StorageDTO permanentStorage) {
        String stagingBucket = stagingStorage.getBucket();
        String stagingPath = stagingStorage.getPath();
        String stagingKey = getKey(fileName, stagingPath);
        validateFileName(stagingKey);

        String permanentBucket = permanentStorage.getBucket();
        String permanentPath = permanentStorage.getPath();
        String permanentKey = getKey(fileName, permanentPath);
        validateFileName(permanentKey);

        if (!fileExists(fileName, stagingStorage)) {
            throw new RuntimeException("Source file does not exist in staging: " + fileName);
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                CopyObjectRequest copyReq = CopyObjectRequest.builder()
                        .sourceBucket(stagingBucket)
                        .sourceKey(stagingKey)
                        .destinationBucket(permanentBucket)
                        .destinationKey(permanentKey)
                        .build();

                s3Client.copyObject(copyReq);

                if (!fileExists(fileName, permanentStorage)) {
                    throw new RuntimeException("Move verification failed - file not found in permanent storage after copy");
                }

                deleteFile(fileName, stagingStorage);

                if (fileExists(fileName, stagingStorage)) {
                    throw new RuntimeException("Move verification failed - file still exists in staging after deletion");
                }

                return;
            } catch (Exception e) {
                lastException = e;
                System.err.println("S3 move attempt " + attempt + " failed for file " + fileName + ": " + e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Move interrupted", ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed to move file from staging to permanent storage after " + MAX_RETRY_ATTEMPTS + " attempts: " + fileName, lastException);
    }

    public String generatePresignedUrl(String fileName, StorageDTO permanentStorage) {
        String bucket = permanentStorage.getBucket();
        String path = permanentStorage.getPath();
        String key = getKey(fileName, path);
        validateFileName(key);

        return String.format("https://%s.s3.amazonaws.com/%s", bucket, key);
    }
}
