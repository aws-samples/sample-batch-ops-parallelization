
import { App, Stack, StackProps } from 'aws-cdk-lib';
import { ARecord, CrossAccountZoneDelegationRecord, HostedZone, RecordTarget } from 'aws-cdk-lib/aws-route53';
import { Role } from 'aws-cdk-lib/aws-iam';
import { DELEGATION_ROLE_ARN, DNS_UPDATE_ROLE_NAME, SERVICE_BASE_DOMAIN_NAME } from './constants';
import { Certificate, CertificateValidation } from 'aws-cdk-lib/aws-certificatemanager';
import { DomainName, EndpointType, SecurityPolicy, RestApi } from 'aws-cdk-lib/aws-apigateway';
import { ApiGatewayDomain } from 'aws-cdk-lib/aws-route53-targets';


export class SubdomainStageStack extends Stack {
  readonly disambiguator: string;
  public readonly superNovaHostedZone: HostedZone;
  public readonly certificate: Certificate;

  constructor(scope: App, id: string, api: RestApi, props?: StackProps,) {
    super(scope, id);

    this.superNovaHostedZone = new HostedZone(this, 'HostedZone', {
      zoneName: SERVICE_BASE_DOMAIN_NAME,
      comment: `DNS for SERVICE_BASE_DOMAIN_NAME`,
    });

    this.certificate = new Certificate(this, 'Certificate', {
      domainName: SERVICE_BASE_DOMAIN_NAME,
      validation: CertificateValidation.fromDns(this.superNovaHostedZone),
    });

    const customDomain = new DomainName(this, 'ApiCustomDomain', {
      domainName: SERVICE_BASE_DOMAIN_NAME,
      certificate: this.certificate,
      endpointType: EndpointType.REGIONAL,
      securityPolicy: SecurityPolicy.TLS_1_2,
    });

    customDomain.addBasePathMapping(api);

    new ARecord(this, 'CustomDomainAliasRecordV2', {
      recordName: SERVICE_BASE_DOMAIN_NAME,
      zone: this.superNovaHostedZone,
      target: RecordTarget.fromAlias(new ApiGatewayDomain(customDomain)),
    });
  }
}