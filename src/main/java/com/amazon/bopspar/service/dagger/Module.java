package com.amazon.bopspar.service.dagger;


import com.amazon.bopspar.persistence.ddb.BaseDynamoDBFacade;
import com.amazon.bopspar.persistence.ddb.WorkflowRepository;
import com.amazon.bopspar.persistence.manager.WorkflowManager;
import com.amazon.bopspar.persistence.manager.WorkflowManagerCRUDImpl;
import com.amazon.bopspar.service.activity.WorkflowActivity;
import com.amazon.bopspar.service.resources.auth.S3ClientFactory;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManager;
import com.amazon.bopspar.service.resources.monitor.S3MonitorManagerFactory;
import com.amazon.bopspar.service.resources.monitor.bops.BopsMonitorManager;
import com.amazon.bopspar.service.resources.monitor.cloudwatch.CloudWatchManager;
import com.amazon.bopspar.service.resources.monitor.crr.CrrMonitorManager;
import com.amazon.bopspar.service.resources.monitor.dashboard.CloudWatchDashboardManager;
import com.amazon.bopspar.service.resources.monitor.dashboard.S3AcceleratorDashboard;
import com.amazon.bopspar.service.resources.replication.S3PostReplicationService;
import com.amazon.bopspar.service.resources.replication.S3ReplicationConfigurator;
import com.amazon.bopspar.service.resources.replication.bops.S3BatchOperationsManager;
import com.amazon.bopspar.service.resources.replication.bucket.S3BucketVersioningManager;
import com.amazon.bopspar.service.resources.replication.policy.S3PolicyManager;
import com.amazon.bopspar.service.resources.replication.rule.S3ReplicationRuleManager;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketImpl;
import com.amazon.bopspar.service.resources.s3.bucket.S3CreateBucketService;
import com.amazon.bopspar.service.resources.s3.configuration.S3ConfigurationService;
import com.amazon.bopspar.service.resources.s3.inventoryreportconfig.S3InventoryReportConfigService;
import com.amazon.bopspar.service.resources.s3.inventoryreportconfig.S3ManifestSplitService;
import com.amazon.bopspar.service.resources.workflow.WorkflowStatusManager;
import com.amazon.bopspar.service.validator.InputValidator;
import com.amazon.bopspar.service.validator.S3RequestValidator;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Provides;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.sfn.SfnClient;

import javax.inject.Singleton;
import java.util.Optional;

@Log4j2
@dagger.Module
public class Module {
    @Provides
    @Singleton
    public DynamoDBMapper provideDynamoDBMapper(final AmazonDynamoDB client) {
        return new DynamoDBMapper(client);
    }

    @Provides
    @Singleton
    public AmazonDynamoDB provideAmazonDynamoDB() {
        return AmazonDynamoDBClientBuilder
            .standard()
            .withRegion(Optional.ofNullable(System.getenv("AWS_REGION"))
                .orElse("us-west-2"))
            .build();
    }



    @Provides
    @Singleton
    public BaseDynamoDBFacade getBaseDynamoDBFacade(final DynamoDBMapper mapper) {
        return new BaseDynamoDBFacade(mapper);
    }

    @Provides
    @Singleton
    public WorkflowRepository getWorkflowRepository(final BaseDynamoDBFacade baseDynamoDBFacade) {
        return new WorkflowRepository(baseDynamoDBFacade);
    }

    @Provides
    @Singleton
    public WorkflowManager getWorkflowManager(final WorkflowRepository workflowRepository,
                                              final SfnClient sfnClient,
                                              final Gson gson) {
        return new WorkflowManagerCRUDImpl(workflowRepository, sfnClient, gson);
    }

    @Provides
    @Singleton
    public InputValidator provideInputValidator() {
        return new InputValidator();
    }

    @Provides
    @Singleton
    public S3RequestValidator provideS3RequestValidator() {
        return new S3RequestValidator();
    }

    @Provides
    @Singleton
    public S3CreateBucketImpl getS3CreateBucketImpl() {
        return new S3CreateBucketImpl();
    }

    @Provides
    @Singleton
    public S3CreateBucketService getS3CreateBucketService(final S3CreateBucketImpl createBucketImpl) {
        return new S3CreateBucketService(createBucketImpl);
    }

    @Provides
    @Singleton
    public S3MonitorManagerFactory provideS3MonitorManagerFactory() {
        return workflowModel -> S3MonitorManager.builder()
                .workflowModel(workflowModel)
                .build();
    }

    @Provides
    @Singleton
    public CloudWatchDashboardManager provideCloudWatchDashboardManager(
            final S3AcceleratorDashboard s3AcceleratorDashboard) {
        return new CloudWatchDashboardManager(s3AcceleratorDashboard);
    }

    @Provides
    @Singleton
    public S3AcceleratorDashboard provideS3AcceleratorDashboard() {
        return new S3AcceleratorDashboard();
    }

    @Provides
    @Singleton
    public BopsMonitorManager provideBopsMonitorManager() {
        return new BopsMonitorManager();
    }

    @Provides
    @Singleton
    public CrrMonitorManager provideCrrMonitorManager() {
        return new CrrMonitorManager();
    }

    @Provides
    @Singleton
    public CloudWatchManager provideCloudWatchManager() {
        return new CloudWatchManager();
    }

    @Provides
    @Singleton
    public WorkflowStatusManager provideWorkflowStatusManager(final WorkflowRepository workflowRepository) {
        return new WorkflowStatusManager(workflowRepository);
    }

    @Provides
    @Singleton
    public S3ClientFactory getS3ClientFactory() {
        return new S3ClientFactory();
    }

    @Provides
    @Singleton
    public S3ConfigurationService getS3ConfigurationService() {
        return new S3ConfigurationService();
    }

    @Provides
    @Singleton
    public S3BatchOperationsManager provideS3BatchOperationsManager() {
        return new S3BatchOperationsManager();
    }

    @Provides
    @Singleton
    public S3BucketVersioningManager provideS3BucketVersioningManager() {
        return new S3BucketVersioningManager();
    }

    @Provides
    @Singleton
    public S3PolicyManager provideS3PolicyManager() {
        return new S3PolicyManager();
    }

    @Provides
    @Singleton
    public S3ReplicationRuleManager provideS3ReplicationRuleManager() {
        return new S3ReplicationRuleManager();
    }

    @Provides
    @Singleton
    public S3ReplicationConfigurator provideS3ReplicationConfigurator(
        final S3BatchOperationsManager s3BatchOperationsManager,
        final S3BucketVersioningManager s3BucketVersioningManager,
        final S3PolicyManager s3PolicyManager,
        final S3ReplicationRuleManager s3ReplicationRuleManager) {
        return new S3ReplicationConfigurator(s3BatchOperationsManager,
            s3BucketVersioningManager, s3PolicyManager, s3ReplicationRuleManager);
    }

    @Provides
    @Singleton
    public S3PostReplicationService getS3PostReplicationService() {
        return new S3PostReplicationService();
    }

    @Provides
    @Singleton
    public WorkflowActivity provideWorkflowActivity(
        WorkflowManager workflowManager,
        InputValidator inputValidator,
        S3ClientFactory s3ClientFactory,
        S3RequestValidator s3RequestValidator,
        WorkflowRepository workflowRepository,
        S3ReplicationConfigurator s3ReplicationConfigurator
    ) {
        return new WorkflowActivity(
            workflowManager,
            inputValidator,
            s3ClientFactory,
            s3RequestValidator,
            workflowRepository,
            s3ReplicationConfigurator
        );
    }

    @Provides
    @Singleton
    public Gson provideGson() {
        return new GsonBuilder().create();
    }

    @Provides
    @Singleton
    public SfnClient provideSfnClient() {
        return SfnClient.builder()
            .build();
    }

    @Provides
    @Singleton
    public S3ManifestSplitService getS3ManifestSplitService() {
        return new S3ManifestSplitService();
    }

    @Provides
    @Singleton
    public S3InventoryReportConfigService getInventoryReportConfigService() {
        return new S3InventoryReportConfigService();
    }
}