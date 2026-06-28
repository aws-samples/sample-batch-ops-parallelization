export enum CREATE_BUCKET_LAMBDA {
    componentName = 'S3CreateBucket',
    lambdaName = 'S3CreateBucketLambda',
    handler = 'com.amazon.bopspar.service.lambda.S3CreateBucketLambda::handleRequest',
  }
  export enum CONFIGURE_BUCKET_LAMBDA {
    componentName = 'S3ConfigureBucket',
    lambdaName = 'S3ConfigureBucketLambda',
    handler = 'com.amazon.bopspar.service.lambda.S3ConfigureBucketLambda::handleRequest',
    packageName = "packageName",
  }
  export enum MONITOR_LAMBDA {
    componentName = 'S3Monitor',
    lambdaName = 'BOPSParallelMonitorLambda',
    handler = 'com.amazon.bopspar.service.lambda.BOPSParallelMonitorLambda::handleRequest',
    packageName = "packageName",
  }
  
  export enum SETUP_REPLICATION_LAMBDA {
    componentName = 'S3ReplicationSetup',
    lambdaName = 'S3ReplicationSetupLambda',
    handler = 'com.amazon.bopspar.service.lambda.S3ReplicationSetupLambda::handleRequest',
    packageName = "packageName",
  }
  
  export enum SETUP_ROLLBACK_LAMBDA {
    componentName = 'S3RollbackSetup',
    lambdaName = 'S3RollbackSetupLambda',
    handler = 'com.amazon.bopspar.service.lambda.S3RollbackReplicationSetupLambda::handleRequest',
    packageName = "packageName",
  }
  
  export enum POST_REPLICATION_LAMBDA {
    componentName = 'S3PostReplication',
    lambdaName = 'S3PostReplicationLambda',
    handler = 'com.amazon.bopspar.service.lambda.S3PostReplicationLambda::handleRequest',
    packageName = "packageName",
  }

export enum WAIT_FOR_CUSTOMER_ACK_LAMBDA {
  componentName = 'WaitForCustomerAck',
  lambdaName = 'WaitForCustomerAckLambda',
  handler = 'com.amazon.bopspar.service.lambda.WaitForCustomerAckLambda::handleRequest',
}
