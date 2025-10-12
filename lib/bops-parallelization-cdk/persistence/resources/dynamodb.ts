import { IKey } from 'aws-cdk-lib/aws-kms';
import { ConstructBaseProps, WORKFLOW_TABLE_PROPS } from '../constants';
import { AttributeType, BillingMode, ITable, Table, TableEncryption } from 'aws-cdk-lib/aws-dynamodb';
import { CfnOutput, RemovalPolicy } from 'aws-cdk-lib';
import { Construct } from 'constructs';


export class DynamoDBResources extends Construct {
  readonly disambiguator: string;
  readonly props: ConstructBaseProps;
  readonly executionTable: ITable;

  constructor(parent: Construct, id: string) {
    super(parent, id);

    this.executionTable = this.createWorkflowTable();
  }

  private createWorkflowTable(): ITable {
    const id = WORKFLOW_TABLE_PROPS.NAME;
    const table = new Table(this, id, {
      tableName: id,
      encryption: TableEncryption.DEFAULT,
      billingMode: BillingMode.PAY_PER_REQUEST,
      removalPolicy: RemovalPolicy.RETAIN,
      partitionKey: {
        name: WORKFLOW_TABLE_PROPS.PK,
        type: AttributeType.STRING,
      },
      sortKey: {
        name: WORKFLOW_TABLE_PROPS.SK,
        type: AttributeType.STRING,
      },
    });

    const exportName = WORKFLOW_TABLE_PROPS.EXPORT_NAME_PREFIX;
    const tableArn = table.tableArn;
    new CfnOutput(this, exportName, {
      value: tableArn,
      exportName: exportName,
    });
    return table;
  }
}