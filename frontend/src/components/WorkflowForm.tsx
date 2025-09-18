import { useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { workflowService } from '../services'
import type { RegionOption, CreateWorkflowRequest } from '../types'
import { regions } from '../types'

// Validation patterns
const ACCOUNT_NUMBER_PATTERN = /^\d{12}$/;

// Import Cloudscape components
import Container from '@cloudscape-design/components/container'
import SpaceBetween from '@cloudscape-design/components/space-between'
import Button from '@cloudscape-design/components/button'
import Input from '@cloudscape-design/components/input'
import Form from '@cloudscape-design/components/form'
import FormField from '@cloudscape-design/components/form-field'
import Header from '@cloudscape-design/components/header'
import Select from '@cloudscape-design/components/select'
import Checkbox from '@cloudscape-design/components/checkbox'
import type { SelectProps } from '@cloudscape-design/components/select'
import type { FlashbarProps } from '@cloudscape-design/components/flashbar'
import { useNavigate } from 'react-router-dom'
import { toWorkflowFromCreateWorkflowRequest } from '../utils/utils'
import { Link } from '@cloudscape-design/components'

interface WorkflowFormProps {
  addNotification?: (notification: FlashbarProps.MessageDefinition) => void;
  clearNotifications?: () => void;
}

function WorkflowForm({ addNotification, clearNotifications }: WorkflowFormProps) {
  const navigate = useNavigate();
  const { 
    control, 
    handleSubmit, 
    formState: { errors },
    setValue
  } = useForm<CreateWorkflowRequest>({
    defaultValues: {
      sourceBucketName: '',
      destBucketName: '',
      sourceAccountNumber: '',
      destAccountNumber: '',
      sourceRegion: '',
      destRegion: '',
      runtimeConfig: {
        isReplicationTimeControlEnabled: false
      }
    },
    mode: 'onChange'
  });
  
  // UI state
  const [loading, setLoading] = useState(false);
  const [sourceRegionOption, setSourceRegionOption] = useState<RegionOption | null>(null);
  const [destRegionOption, setDestRegionOption] = useState<RegionOption | null>(null);

  // Handle region selection
  const handleSourceRegionChange: SelectProps['onChange'] = (event) => {
    const option = event.detail.selectedOption as RegionOption;
    setSourceRegionOption(option);
    if (option) {
      setValue('sourceRegion', option.value, { 
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true
      });
    }
  };

  const handleDestRegionChange: SelectProps['onChange'] = (event) => {
    const option = event.detail.selectedOption as RegionOption;
    setDestRegionOption(option);
    if (option) {
      setValue('destRegion', option.value, { 
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true
      });
    }
  };

  // Handle form submission
  const onSubmit = async (data: CreateWorkflowRequest) => {
    setLoading(true);
    if (clearNotifications) {
      clearNotifications();
    }
    
    try {
      const workflow = toWorkflowFromCreateWorkflowRequest(data);
      await workflowService.createWorkflow(workflow);
      if (addNotification) {
        addNotification({
          type: 'success',
          header: 'Success',
          content: 'Workflow created successfully!',
          id: 'success-message',
          dismissible: true,
          dismissLabel: 'Dismiss message',
          action: (
            <Button
              onClick={() => {
                navigate(`/workflows/view?namespaceID=${workflow.namespaceID}&workflowName=${workflow.workflowName}`);
                if (clearNotifications) {
                  clearNotifications();
                }
              }}
            >
              View Details
            </Button>
          )
        });
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (error) {
      console.error('Error submitting workflow:', error);
      if (addNotification) {
        addNotification({
          type: 'error',
          header: 'Error',
          content: 'Failed to create workflow. Please try again.',
          id: 'error-message',
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container>
      <Form
        actions={
          <SpaceBetween direction="horizontal" size="xs">
            <Button variant="link">Cancel</Button>
            <Button 
              variant="primary" 
              onClick={() => handleSubmit(onSubmit)()} 
              loading={loading}
            >
              Create workflow
            </Button>
          </SpaceBetween>
        }
      >
        <SpaceBetween size="l">
          <Container header={<Header variant="h2">Source Configuration</Header>}>
            <SpaceBetween size="l">
              <FormField 
                label="Source Bucket Name" 
                description="Name of the source S3 bucket"
                errorText={errors.sourceBucketName?.message}
              >
                <Controller
                  name="sourceBucketName"
                  control={control}
                  rules={{ 
                    required: "Source Bucket Name is required"
                  }}
                  render={({ field }) => (
                    <Input
                      value={field.value}
                      onChange={event => field.onChange(event.detail.value)}
                      onBlur={field.onBlur}
                      invalid={!!errors.sourceBucketName}
                      placeholder='source-bucket'
                    />
                  )}
                />
              </FormField>
              
              <FormField 
                label="Source Account Number" 
                description="AWS account number for the source"
                errorText={errors.sourceAccountNumber?.message}
              >
                <Controller
                  name="sourceAccountNumber"
                  control={control}
                  rules={{ 
                    required: "Source Account Number is required",
                    pattern: {
                      value: ACCOUNT_NUMBER_PATTERN,
                      message: "Account number must be exactly 12 digits"
                    }
                  }}
                  render={({ field }) => (
                    <Input
                      value={field.value}
                      onChange={event => field.onChange(event.detail.value)}
                      onBlur={field.onBlur}
                      invalid={!!errors.sourceAccountNumber}
                      placeholder='012345678901'
                    />
                  )}
                />
              </FormField>
              
              <FormField 
                label="Source Region" 
                description="AWS region for the source bucket"
                errorText={errors.sourceRegion?.message}
              >
                <Controller
                  name="sourceRegion"
                  control={control}
                  rules={{ required: "Source Region is required" }}
                  render={({ }) => (
                    <Select
                      selectedOption={sourceRegionOption}
                      onChange={handleSourceRegionChange}
                      options={regions}
                      filteringType="auto"
                      invalid={!!errors.sourceRegion}
                    />
                  )}
                />
              </FormField>

              <FormField>
                <Controller
                  name="runtimeConfig.isReplicationTimeControlEnabled"
                  control={control}
                  render={({ field }) => (
                    <Checkbox
                      description={
                        <>
                          Replicates 99.99% of new objects within 15 minutes and includes replication metrics. Additional fees will apply.&nbsp;
                          <Link
                            external
                            href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/replication-time-control.html"
                          >
                            Learn more
                          </Link>
                        </>
                      }
                      checked={field.value}
                      onChange={event => field.onChange(event.detail.checked)}
                    >
                      Replication Time Control (RTC)
                    </Checkbox>
                  )}
                />
              </FormField>
            </SpaceBetween>
          </Container>
          
          <Container header={<Header variant="h2">Destination Configuration</Header>}>
            <SpaceBetween size="l">
              <FormField 
                label="Destination Bucket Name" 
                description="Name of the destination S3 bucket"
                errorText={errors.destBucketName?.message}
              >
                <Controller
                  name="destBucketName"
                  control={control}
                  rules={{ 
                    required: "Destination Bucket Name is required"
                  }}
                  render={({ field }) => (
                    <Input
                      value={field.value}
                      onChange={event => field.onChange(event.detail.value)}
                      onBlur={field.onBlur}
                      invalid={!!errors.destBucketName}
                      placeholder='destination-bucket'
                    />
                  )}
                />
              </FormField>
              
              <FormField 
                label="Destination Account Number" 
                description="AWS account number for the destination"
                errorText={errors.destAccountNumber?.message}
              >
                <Controller
                  name="destAccountNumber"
                  control={control}
                  rules={{ 
                    required: "Destination Account Number is required",
                    pattern: {
                      value: ACCOUNT_NUMBER_PATTERN,
                      message: "Account number must be exactly 12 digits"
                    }
                  }}
                  render={({ field }) => (
                    <Input
                      value={field.value}
                      onChange={event => field.onChange(event.detail.value)}
                      onBlur={field.onBlur}
                      invalid={!!errors.destAccountNumber}
                      placeholder='012345678901'
                    />
                  )}
                />
              </FormField>
              
              <FormField 
                label="Destination Region" 
                description="AWS region for the destination bucket"
                errorText={errors.destRegion?.message}
              >
                <Controller
                  name="destRegion"
                  control={control}
                  rules={{ required: "Destination Region is required" }}
                  render={({  }) => (
                    <Select
                      selectedOption={destRegionOption}
                      onChange={handleDestRegionChange}
                      options={regions}
                      filteringType="auto"
                      invalid={!!errors.destRegion}
                    />
                  )}
                />
              </FormField>
            </SpaceBetween>
          </Container>
        </SpaceBetween>
      </Form>
    </Container>
  );
}

export default WorkflowForm;
