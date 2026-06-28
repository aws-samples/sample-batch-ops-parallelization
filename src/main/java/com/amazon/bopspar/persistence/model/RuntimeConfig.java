package com.amazon.bopspar.persistence.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBDocument
public class RuntimeConfig implements Serializable {
    private String dashboardUrl;
    private String manifestLocation;
    private boolean skipBucketOwnershipValidationAndCopy;
    private boolean isReplicationTimeControlEnabled;

    public com.amazon.bopspar.model.RuntimeConfig toSmithyModel() {
        return com.amazon.bopspar.model.RuntimeConfig.builder()
                .withDashboardUrl(this.dashboardUrl)
                .withManifestLocation(this.manifestLocation)
                .withSkipBucketOwnershipValidationAndCopy(this.skipBucketOwnershipValidationAndCopy)
                .withReplicationTimeControlEnabled(this.isReplicationTimeControlEnabled)
                .build();
    }
}
