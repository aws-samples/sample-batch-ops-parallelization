package com.amazon.bopspar.service.resources.monitor;

import com.amazon.bopspar.persistence.model.WorkFlowModel;
/**
 * S3A MonitorManager Factory Interface.
 *
 */
public interface S3MonitorManagerFactory {
    S3MonitorManager create(WorkFlowModel workflowModel);
}
