import { App, Stack, StackProps } from 'aws-cdk-lib';
import { DynamoDBResources } from './resources/dynamodb';
import { ConstructBaseProps, StackBaseProps } from './constants';
import { Construct } from 'constructs';

export class PersistenceStack extends Stack {
  readonly disambiguator: string;
  readonly props: ConstructBaseProps;
  readonly dynamoDBResources: DynamoDBResources;

  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.dynamoDBResources = this.createDynamoDBResources();
  }

  private createDynamoDBResources() {
    return new DynamoDBResources(this, 'DynamoDBResources');
  }
}
