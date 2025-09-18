package com.amazon.tdm.s3a.service.resources.replication;

import com.amazon.tdm.s3a.persistence.ddb.WorkflowRepository;
import com.amazon.tdm.s3a.persistence.model.WorkFlowModel;
import com.amazon.tdm.s3a.service.resources.replication.bops.S3BatchOperationsManager;
import com.amazon.tdm.s3a.service.resources.replication.policy.S3PolicyManager;
import com.amazon.tdm.s3a.service.resources.replication.rule.S3ReplicationRuleManager;
import com.amazon.tdm.s3a.service.resources.replication.bucket.S3BucketVersioningManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutBucketVersioningResponse;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.CreateJobResponse;
import software.amazon.awssdk.services.s3control.model.RequestedJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;


import static org.mockito.Mockito.*;

public class S3ReplicationConfiguratorTest {

    @Mock
    private S3BatchOperationsManager s3BatchOperationsManager;
    @Mock
    private S3BucketVersioningManager s3BucketVersioningManager;
    @Mock
    private S3PolicyManager s3PolicyManager;
    @Mock
    private S3ReplicationRuleManager s3ReplicationRuleManager;
    @Mock
    private S3Client s3Client;
    @Mock
    private S3ControlClient s3ControlClient;
    @Mock
    private KmsClient kmsClient;
    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private WorkFlowModel workflowModel;
    @Mock
    private PutBucketVersioningResponse versioningResponse;
    @Mock
    private CreateJobResponse createJobResponse;
    @Mock
    private PutBucketPolicyRequest policyRequest;

    private S3ReplicationConfigurator configurator;
    private static final String DEST_BUCKET_NAME = "destBucket";
    private static final String SOURCE_ACCOUNT = "123456789012";
    private static final String BOPS_ROLE = "arn:aws:iam::123456789012:role/bops-role";

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        configurator = new S3ReplicationConfigurator(
            s3BatchOperationsManager,
            s3BucketVersioningManager,
            s3PolicyManager,
            s3ReplicationRuleManager
        );
    }

    @Test
    public void testUpdateDestKeyPolicyIfBucketIsKmsEncrypted() {
        configurator.updateDestKeyPolicyIfBucketIsKmsEncrypted(workflowModel, s3Client, kmsClient);
        verify(s3PolicyManager).updateDestKeyPolicyIfBucketIsKmsEncrypted(workflowModel, s3Client, kmsClient);
    }

    @Test
    public void testEnableBucketVersioning() {
        when(s3BucketVersioningManager.enableBucketVersioning(workflowModel, s3Client))
            .thenReturn(versioningResponse);

        PutBucketVersioningResponse response = configurator.enableBucketVersioning(workflowModel, s3Client);

        verify(s3BucketVersioningManager).enableBucketVersioning(workflowModel, s3Client);
        assert(response == versioningResponse);
    }

    @Test
    public void testAddCrossAccountBucketPolicy() {
        configurator.addCrossAccountBucketPolicy(workflowModel, s3Client, DEST_BUCKET_NAME);
        verify(s3PolicyManager).addCrossAccountBucketPolicy(workflowModel, s3Client, DEST_BUCKET_NAME);
    }

    @Test
    public void testSetupLiveReplication() {
        configurator.setupLiveReplication(workflowModel, s3Client, s3Client, BOPS_ROLE);
        verify(s3ReplicationRuleManager).setupLiveReplication(workflowModel, s3Client, s3Client, BOPS_ROLE);
    }

    @Test
    public void testSetupBOPSJob() {
        when(s3BatchOperationsManager.setupBOPSJob(workflowModel, s3ControlClient, workflowRepository, BOPS_ROLE))
            .thenReturn(createJobResponse);

        CreateJobResponse response = configurator.setupBOPSJob(workflowModel, s3ControlClient, workflowRepository, BOPS_ROLE);

        verify(s3BatchOperationsManager).setupBOPSJob(workflowModel, s3ControlClient, workflowRepository, BOPS_ROLE);
        assert(response == createJobResponse);
    }

    @Test
    public void testSetupBOPSJobWithManifest() {
        when(s3BatchOperationsManager.setupBOPSJob(workflowModel, s3Client, s3ControlClient, workflowRepository, BOPS_ROLE))
            .thenReturn(createJobResponse);

        CreateJobResponse response = configurator.setupBOPSJobWithManifest(
            workflowModel, s3Client, s3ControlClient, workflowRepository, BOPS_ROLE);

        verify(s3BatchOperationsManager).setupBOPSJob(
            workflowModel, s3Client, s3ControlClient, workflowRepository, BOPS_ROLE);
        assert(response == createJobResponse);
    }

    @Test
    public void testBuildCrossAccountPutBucketPolicyRequest() {
        when(s3PolicyManager.buildCrossAccountPutBucketPolicyRequest(DEST_BUCKET_NAME, SOURCE_ACCOUNT, s3Client))
            .thenReturn(policyRequest);

        PutBucketPolicyRequest response = configurator.buildCrossAccountPutBucketPolicyRequest(
            DEST_BUCKET_NAME, SOURCE_ACCOUNT, s3Client);

        verify(s3PolicyManager).buildCrossAccountPutBucketPolicyRequest(DEST_BUCKET_NAME, SOURCE_ACCOUNT, s3Client);
        assert(response == policyRequest);
    }

    @Test
    public void testRemoveReplicationRule() {
        configurator.removeReplicationRule(s3Client, workflowModel);
        verify(s3ReplicationRuleManager).removeReplicationRule(s3Client, workflowModel);
    }

    @Test
    public void testBatchUpdateJobStatus() {
        RequestedJobStatus status = RequestedJobStatus.READY;
        configurator.batchUpdateJobStatus(s3ControlClient, workflowModel, status);
        verify(s3BatchOperationsManager).batchUpdateJobStatus(s3ControlClient, workflowModel, status);
    }
}

