package com.amazon.tdm.s3a.service.dagger;

import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.manager.WorkflowManager;
import com.amazon.tdm.s3a.service.activity.WorkflowActivity;
import com.amazon.tdm.s3a.service.resources.auth.S3ClientFactory;
import com.amazon.tdm.s3a.service.resources.monitor.S3MonitorManagerFactory;
import com.amazon.tdm.s3a.service.resources.monitor.bops.BopsMonitorManager;
import com.amazon.tdm.s3a.service.resources.monitor.cloudwatch.CloudWatchManager;
import com.amazon.tdm.s3a.service.resources.monitor.crr.CrrMonitorManager;
import com.amazon.tdm.s3a.service.resources.monitor.dashboard.CloudWatchDashboardManager;
import com.amazon.tdm.s3a.service.resources.replication.S3PostReplicationService;
import com.amazon.tdm.s3a.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketImpl;
import com.amazon.tdm.s3a.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.tdm.s3a.service.resources.s3.configuration.S3ConfigurationService;
import com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig.S3InventoryReportConfigService;
import com.amazon.tdm.s3a.service.resources.s3.inventoryreportconfig.S3ManifestSplitService;
import com.amazon.tdm.s3a.service.resources.workflow.WorkflowStatusManager;
import com.google.gson.Gson;
import dagger.Component;
import software.amazon.awssdk.services.sfn.SfnClient;

import javax.inject.Singleton;

@Singleton
@Component(modules = {Module.class})
public interface LambdaComponent {

    WorkflowManager getWorkflowManager();

    WorkflowRepository getWorkflowRepository();

    S3CreateBucketImpl getS3CreateBucketImpl();

    S3CreateBucketService getS3CreateBucketService();

    S3MonitorManagerFactory getS3MonitorManagerFactory();

    CloudWatchDashboardManager getCloudWatchDashboardManager();

    BopsMonitorManager getBopsMonitorManager();

    CrrMonitorManager getCrrMonitorManager();

    CloudWatchManager getCloudWatchManager();

    WorkflowStatusManager getWorkflowStatusManager();

    S3ReplicationConfigurator getS3ReplicationConfigurator();

    S3ClientFactory getS3ClientFactory();

    S3ConfigurationService getS3ConfigurationService();

    S3PostReplicationService getS3PostReplicationService();

    S3ManifestSplitService getS3ManifestSplitService();

    S3InventoryReportConfigService getS3InventoryReportConfigService();

    WorkflowActivity getWorkflowActivity();

    Gson getGson();

    SfnClient getSfnClient();
}