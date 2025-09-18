/**
 * API related interfaces and types
 */

// Define the region option type
export interface RegionOption {
  label: string;
  value: string;
}

// Define the regions for dropdown selection
export const regions: RegionOption[] = [
  { label: 'us-east-1', value: 'us-east-1' },
  { label: 'us-east-2', value: 'us-east-2' },
  { label: 'us-west-1', value: 'us-west-1' },
  { label: 'us-west-2', value: 'us-west-2' },
  { label: 'af-south-1', value: 'af-south-1' },
  { label: 'ap-east-1', value: 'ap-east-1' },
  { label: 'ap-south-1', value: 'ap-south-1' },
  { label: 'ap-northeast-2', value: 'ap-northeast-2' },
  { label: 'ap-southeast-1', value: 'ap-southeast-1' },
  { label: 'ap-southeast-2', value: 'ap-southeast-2' },
  { label: 'ap-northeast-1', value: 'ap-northeast-1' },
  { label: 'ca-central-1', value: 'ca-central-1' },
  { label: 'eu-central-1', value: 'eu-central-1' },
  { label: 'eu-west-1', value: 'eu-west-1' },
  { label: 'eu-west-2', value: 'eu-west-2' },
  { label: 'eu-south-1', value: 'eu-south-1' },
  { label: 'eu-west-3', value: 'eu-west-3' },
  { label: 'eu-north-1', value: 'eu-north-1' },
  { label: 'eu-south-2', value: 'eu-south-2' },
  { label: 'me-south-1', value: 'me-south-1' },
  { label: 'sa-east-1', value: 'sa-east-1' }
];
