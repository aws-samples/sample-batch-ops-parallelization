package com.amazon.tdm.s3a.service.resources.replication.bops;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManifestFileMetadata {
    String key;
    String etag;
}