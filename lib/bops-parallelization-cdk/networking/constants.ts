export const SERVICE_BASE_DOMAIN_NAME = '';
export const DNS_UPDATE_ROLE_NAME = 'DNSDelegationRole';

export const DELEGATION_ROLE_ARN = (accountId: string, roleName: string): string => {
  return `arn:aws:iam::${accountId}:role/${roleName}`;
};
