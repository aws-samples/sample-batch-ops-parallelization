package com.amazon.bopspar.service.resources.replication.bops;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ManifestFileMetadata {
    String key;
    String etag;
}