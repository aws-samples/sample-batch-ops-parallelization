package com.amazon.tdm.s3a.service.resources.monitor;

import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
/**
 * S3A MonitorManager Factory Interface.
 *
 */
public interface S3MonitorManagerFactory {
    S3MonitorManager create(WorkFlowModel workflowModel);
}
