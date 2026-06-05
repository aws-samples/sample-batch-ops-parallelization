package com.amazon.bopspar.service.resources.replication.bucket;


/**
 * Enumeration of supported encryption types for S3 objects.
 * Represents the different encryption mechanisms that can be applied to objects
 * in Amazon S3 buckets.
 */
public enum EncryptionType {
    /**
     * Server-Side Encryption with AWS KMS managed keys (SSE-KMS).
     */
    SSE_KMS,

    /**
     * Server-Side Encryption with Amazon S3 managed keys (SSE-S3).
     */
    SSE_S3,

    /**
     * Indicates no encryption is applied to the object.
     */
    NONE,

    /**
     * Indicates the encryption type could not be determined or is not recognized.
     */
    UNKNOWN
}
