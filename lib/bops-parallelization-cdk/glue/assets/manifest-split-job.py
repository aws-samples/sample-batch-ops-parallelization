import sys
from awsglue.transforms import *
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
from awsglue.dynamicframe import DynamicFrame
from pyspark.sql.functions import *
from awsglue.transforms import SelectFields
from botocore.exceptions import ClientError
from urllib.parse import quote
import boto3
import math
import json
import hashlib
import logging

#logging
logging.basicConfig(level=logging.INFO)
logger = logger = logging.getLogger(__name__)

# row_limit is optional so it is resolved separately to keep getResolvedOptions from failing when it is absent
optional_args = ['row_limit'] if '--row_limit' in sys.argv else []
args = getResolvedOptions(
    sys.argv,
    ['workflow_name', 'workflow_namespace_id', 'inventory_bucket', 'inventory_report_manifest_location'] + optional_args
)

inventory_bucket = args['inventory_bucket']
workflow_name = args['workflow_name']
workflow_namespace_id = args['workflow_namespace_id']
# Use s3a instead of s3 path since that supports files >5gb and has better underlying infrastructure
destination_bucket_prefix = f"s3a://{inventory_bucket}/split_output/"
inventory_report_manifest_location = args['inventory_report_manifest_location']

# ROW_LIMIT is the approximate max number of rows that should be included in one single output CSV file.
# It is supplied at deploy time via the CDK Glue job config (--row_limit) and defaults to 5 billion.
# The value is clamped to the supported 1 billion - 5 billion range to guard against misconfiguration.
ROW_LIMIT_DEFAULT = 5_000_000_000
ROW_LIMIT_MIN = 1_000_000_000
ROW_LIMIT_MAX = 5_000_000_000


def resolve_row_limit(raw_value):
    """Parse and clamp the configured row limit to the supported 1B-5B range."""
    if raw_value is None:
        return ROW_LIMIT_DEFAULT
    try:
        parsed = int(raw_value)
    except (TypeError, ValueError):
        logger.warning(f"Invalid row_limit '{raw_value}', falling back to default {ROW_LIMIT_DEFAULT}")
        return ROW_LIMIT_DEFAULT
    clamped = max(ROW_LIMIT_MIN, min(parsed, ROW_LIMIT_MAX))
    if clamped != parsed:
        logger.warning(f"row_limit {parsed} outside supported range "
                       f"[{ROW_LIMIT_MIN}, {ROW_LIMIT_MAX}], clamped to {clamped}")
    return clamped


ROW_LIMIT = resolve_row_limit(args.get('row_limit'))
logger.info(f"Using ROW_LIMIT={ROW_LIMIT}")

# WRITE_THRESHOLD is the number of rows to store in memory until starting write and unpersisting data
WRITE_THRESHOLD = 5_000_000_000

# How many files to read in at a time
FILE_CHUNK_SIZE = 50

# Derive the region from the Glue job's own execution environment so DynamoDB
# follows wherever the stack (and the S3A_WORKFLOWS table) is deployed, instead
# of being pinned to a single region.
region_name = boto3.session.Session().region_name
dynamodb = boto3.resource('dynamodb', region_name=region_name)
s3_client = boto3.client('s3')

def update_dynamodb_job_state(wf_name, namespace_id, manifest_location, state='FINISHED'):
    """Update existing DynamoDB job status"""

    logger.info(f"Updating DynamoDB state to {state} for workflow {wf_name}, namespace_id {namespace_id}")

    try:
        table = dynamodb.Table('S3A_WORKFLOWS')

        # ParseURI() in the SetupReplication Lambda only parses CLI-compatible URLS with the prefix s3://, not s3a://
        manifest_location = manifest_location.replace('s3a://', 's3://')

        response = table.update_item(
            Key={
                'workflowName': wf_name,
                "namespaceID": namespace_id
            },
            UpdateExpression="set #job_state = :s, #rc.#ml = :manifestLocation",  # Use #job_state instead of state
            ExpressionAttributeNames={  # Define the attribute name mapping
                '#job_state': 'state',
                '#rc': 'runtimeConfig',
                '#ml': 'manifestLocation'
            },
            ExpressionAttributeValues={
                ':s': state,
                ':manifestLocation': manifest_location
            },
            ReturnValues="UPDATED_NEW"
        )
        logger.info(f"DynamoDB state update successful for workflow {wf_name}: {response}")

    except Exception as e:
        logger.info(f"Error updating DynamoDB status: {str(e)}")
        raise


def split_data_frame_and_write(spark, file_paths):
    """
    Read 50 files at a time until it reaches the MAX_LIMIT threshold then coalesce
    into 1 partition, sort by "key" and then union to data frame which holds all rows.
    This allows us to reduce out of memory issues when trying to read all the files at once
    and calling repartion on it.
    """
    df_all = None
    df_all_count = 0
    df_batch = None
    current_count = 0
    batch_index = 0
    try:
        for i in range(0, len(file_paths), FILE_CHUNK_SIZE):
            # read first chunk
            chunk_paths = file_paths[i : i + FILE_CHUNK_SIZE]
            df_chunk = spark.read.parquet(*chunk_paths)
            chunk_count = df_chunk.count()
            # accumulate current_count for MAX_LIMIT threshold check
            current_count += chunk_count
            df_all_count += chunk_count
            # union with df_batch until df_batch count is over MAX_LIMIT threshold
            df_batch = df_chunk if df_batch is None else df_batch.union(df_chunk)

            df_chunk = unpersist_df(df_chunk)

            if current_count >= ROW_LIMIT:
                df_batch = transform_data(df_batch)
                # accumulate batch into df_all
                df_all = df_batch if df_all is None else df_all.union(df_batch)
                logger.info(f"Number of partitions: {df_all.rdd.getNumPartitions()}")
                if(df_all_count >= WRITE_THRESHOLD):
                    logger.info(f"Number of rows in current write batch: {df_all_count}")
                    logger.info(f"\n Count of df_all is bigger than: {WRITE_THRESHOLD}, writing to: {destination_bucket_prefix}")
                    write_data_frame(df_all, destination_bucket_prefix)
                    df_all = unpersist_df(df_all)
                    df_all_count = 0
                batch_index += 1
                df_batch = unpersist_df(df_batch)
                current_count = 0

        # get leftover rows if it hasn't reached MAX_LIMIT threshold
        if df_batch is not None and current_count > 0:
            df_batch = transform_data(df_batch)
            logger.info(df_batch.rdd.getNumPartitions())
            df_all = df_batch if df_all is None else df_all.union(df_batch)
            logger.info(f"Number of partitions: {df_all.rdd.getNumPartitions()}")
            df_batch = unpersist_df(df_batch)

        # write the leftover files
        if(df_all is not None and df_all_count > 0):
            logger.info(f"\n Leftover files below WRITE_THRESHOLD, writing to: {destination_bucket_prefix}")
            logger.info(f"Number of rows in current write batch: {df_all_count}")
            write_data_frame(df_all, destination_bucket_prefix)
            df_all = unpersist_df(df_all)

        return
    except Exception as e:
        logger.info(f"Error splitting dynamic frame: {str(e)}")
        raise

@udf(StringType())
def url_encode(s):
    return quote(s)

def transform_data(df_batch):
    # select first 3 columns to remove last 2 columns
    df_batch = df_batch.select(
        df_batch.columns[0],  # bucket name
        df_batch.columns[1],  # object key
        df_batch.columns[2]   # versionId
    )
    # replace NULL type with string type with value "null"
    df_batch = df_batch.withColumn("version_id", when(col("version_id").isNull(), "null").otherwise(col("version_id")))

    # URL encode each column
    df_batch = df_batch.withColumn(df_batch.columns[0], url_encode(col(df_batch.columns[0])))
    df_batch = df_batch.withColumn(df_batch.columns[1], url_encode(col(df_batch.columns[1])))
    df_batch = df_batch.withColumn(df_batch.columns[2], url_encode(col(df_batch.columns[2])))

    # reduce current batch partitions to 1 and sortWithinPartitions with "key" column
    df_batch = df_batch.coalesce(1)

    return df_batch

def unpersist_df(curr_df):
    if(curr_df is not None):
        curr_df.unpersist()
        curr_df = None
    return curr_df

def write_data_frame(df_all, path_prefix):
    df_all.write \
        .mode("append") \
        .option("header", "false") \
        .option("compression", "none") \
        .csv(path_prefix)
    return


def verify_and_load_manifest(inventory_report_manifest_location):
    """Verify and load manifest.json using manifest.checksum"""
    try:

        # Parse bucket and paths from manifest location
        path_parts = inventory_report_manifest_location.replace('s3://', '').split('/')
        bucket = path_parts[0]
        prefix = '/'.join(path_parts[1:])

        # Get manifest.checksum
        checksum_obj = s3_client.get_object(
            Bucket=bucket,
            Key=f"{prefix}manifest.checksum"
        )
        stored_checksum = checksum_obj['Body'].read().decode('utf-8').strip()

        # Get manifest.json
        manifest_obj = s3_client.get_object(
            Bucket=bucket,
            Key=f"{prefix}manifest.json"
        )
        manifest_content = manifest_obj['Body'].read()

        # Calculate manifest checksum
        calculated_checksum = hashlib.md5(manifest_content).hexdigest()

        if calculated_checksum != stored_checksum:
            raise Exception("Manifest checksum verification failed")

        logger.info("Manifest checksum verified successfully")

        # Parse and return manifest JSON
        manifest = json.loads(manifest_content)
        inventory_paths = []
        for file_info in manifest['files']:
            path = f"s3a://{inventory_bucket}/{file_info['key']}"
            inventory_paths.append(path)

        logger.info(f"\nFound {len(inventory_paths)} inventory files in manifest")

        return inventory_paths

    except Exception as e:
        logger.info(f"Error verifying and loading manifest: {str(e)}")
        raise

def main():
    try:
        sc = SparkContext()
        sc._jsc.hadoopConfiguration().set('fs.s3a.fast.upload', 'true')
        sc._jsc.hadoopConfiguration().set('fs.s3a.connection.maximum', '100')

        glueContext = GlueContext(sc)
        spark = glueContext.spark_session
        spark.conf.set("spark.default.parallelism", "960")
        spark.conf.set("spark.sql.shuffle.partitions", "960")
        job = Job(glueContext)
        job.init("split_large_files_job")

        # Get an array of paths to the generated inventory report files from the manifest.json file
        inventory_report_file_paths = verify_and_load_manifest(inventory_report_manifest_location)

        # Process the dynamic frame and get splits
        split_data_frame_and_write(spark, inventory_report_file_paths)

        update_dynamodb_job_state(workflow_name, workflow_namespace_id, destination_bucket_prefix)
        logger.info(f"\nSuccessfully wrote manifest files to {destination_bucket_prefix}")

        job.commit()

    except Exception as e:
        logger.info(f"Error in main execution: {str(e)}")
        raise

if __name__ == "__main__":
    main()
