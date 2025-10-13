/*
All constants for persistence stack resources go here
 */


export interface StackBaseProps {
  readonly stage: string;
  readonly disambiguator: string;
}

export interface StackBaseProps {
  readonly stage: string;
  readonly disambiguator: string;
}

export interface ConstructBaseProps {
  readonly account: string;
  readonly region: string;
  readonly stage: string;
  readonly disambiguator: string;
}

//Dynamo db constants
export enum WORKFLOW_TABLE_PROPS {
  EXPORT_NAME_PREFIX = 'WorkflowTableArn',
  NAME = 'S3A_WORKFLOWS',
  PK = 'workflowName',
  SK = 'namespaceID',
}