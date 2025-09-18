import { useState, useEffect } from 'react'
import { workflowService } from '../services'
import type { WorkflowView } from '../types'

// Import Cloudscape components
import Container from '@cloudscape-design/components/container'
import SpaceBetween from '@cloudscape-design/components/space-between'
import Header from '@cloudscape-design/components/header'
import Alert from '@cloudscape-design/components/alert'
import ColumnLayout from '@cloudscape-design/components/column-layout'
import Box from '@cloudscape-design/components/box'
import Spinner from '@cloudscape-design/components/spinner'
import Button from '@cloudscape-design/components/button'
import StatusIndicator from '@cloudscape-design/components/status-indicator'
import Flashbar from '@cloudscape-design/components/flashbar'
import Modal from '@cloudscape-design/components/modal'
import { getStatusIndicator, toWorkflowViewFromWorkflow, getStatusPopoverContent, getStatusPopoverHeader } from '../utils/utils'
import { Link } from 'react-router-dom'
import type { FlashbarProps } from '@cloudscape-design/components/flashbar'

interface ViewWorkflowProps {
  namespaceID: string;
  workflowName: string;
  onBack?: () => void;
  addNotification?: (notification: FlashbarProps.MessageDefinition) => void;
  clearNotifications?: () => void;
}

function ViewWorkflow({ namespaceID, workflowName, onBack, addNotification }: ViewWorkflowProps) {
  const [workflow, setWorkflow] = useState<WorkflowView | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [isStartWorkflowLoading, setIsStartWorkflowLoading] = useState<boolean>(false);
  const [isConfirmModalVisible, setIsConfirmModalVisible] = useState<boolean>(false);

  useEffect(() => {
    if (namespaceID && workflowName) {
      fetchWorkflow(namespaceID, workflowName)
    }
  }, [namespaceID, workflowName])

  const fetchWorkflow = async (namespaceID: string, workflowName: string) => {
    setLoading(true)
    setError('')

    try {
      // This is a placeholder - you would need to implement this method in workflowService
      const response = await workflowService.getWorkflow(namespaceID, workflowName)
      const workflow = toWorkflowViewFromWorkflow(response.workflow);
      setWorkflow(workflow)
    } catch (error) {
      console.error('Error fetching workflow:', error)
      setError('Failed to fetch workflow details. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleStartWorkflow = async (namespaceID: string, workflowName: string, useManifestSplit: boolean = false) => {
    try {
      setIsStartWorkflowLoading(true);

      if (useManifestSplit) {
        await workflowService.startManifestSplitWorkflow(namespaceID, workflowName);
      } else {
        await workflowService.startWorkflow(namespaceID, workflowName);
      }

      // Show success notification
      if (addNotification) {
        addNotification({
          type: 'success',
          header: 'Success',
          content: 'Successfully started workflow!',
          id: 'start-workflow-success',
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }

      // Refresh workflow data to get updated status
      fetchWorkflow(namespaceID, workflowName);
    } catch (err) {
      console.error('Error starting workflow:', err);

      // Show error notification
      if (addNotification) {
        addNotification({
          type: 'error',
          header: 'Error',
          content: 'Failed to start workflow. Please try again.',
          id: 'start-workflow-error',
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } finally {
      setIsStartWorkflowLoading(false);
    }
  };

  const handleSendControlCommand = async (namespaceID: string, workflowName: string, notificationID: string) => {
    try {
      setIsStartWorkflowLoading(true);
      await workflowService.sendControlCommand(namespaceID, workflowName, notificationID);

      // Show success notification
      if (addNotification) {
        addNotification({
          type: 'success',
          header: 'Success',
          content: 'Successfully acknowledged stop source traffic!',
          id: 'send-control-command-success',
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }

      // Refresh workflow data to get updated status
      fetchWorkflow(namespaceID, workflowName);
    } catch (err) {
      console.error('Error sending control command:', err);

      // Show error notification
      if (addNotification) {
        addNotification({
          type: 'error',
          header: 'Error',
          content: 'Failed to acknowledge stop source traffic. Please try again.',
          id: 'send-control-command-error',
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } finally {
      setIsStartWorkflowLoading(false);
    }
  };

  if (loading) {
    return (
      <Container>
        <SpaceBetween size="m" direction="vertical" alignItems="center">
          <Spinner size="large" />
          <Box>Loading workflow details...</Box>
        </SpaceBetween>
      </Container>
    )
  }

  if (error) {
    return (
      <Container>
        <Alert type="error">
          {error}
        </Alert>
      </Container>
    )
  }

  if (!workflow) {
    return (
      <Container>
        <Alert type="info">
          Select a workflow to view its details.
        </Alert>
      </Container>
    )
  }

  return (
    <Container>
      <SpaceBetween size="l">
        {workflow.status === "WAITING" && (
          <Flashbar
            items={[
              {
                type: "warning",
                header: getStatusPopoverHeader(workflow.status),
                content: getStatusPopoverContent(workflow.status),
                id: "waiting-status-warning",
                dismissible: false
              }
            ]}
          />
        )}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button onClick={onBack} iconName="arrow-left">Back to List</Button>
          <SpaceBetween direction="horizontal" size="xs">
            {workflow.runtimeConfig?.dashboardUrl && (
              <Link
                to={workflow.runtimeConfig.dashboardUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Button
                  iconName="external"
                  ariaLabel="Open workflow dashboard"
                >
                  Open dashboard
                </Button>
              </Link>
            )}
            {workflow.status === "READY" && (
              <Button
                onClick={() => {
                  console.log('Start Workflow clicked for:', workflowName);
                  setIsConfirmModalVisible(true);
                }}
                loading={isStartWorkflowLoading}
                disabled={isStartWorkflowLoading}
              >
                Start workflow
              </Button>
            )}
            {workflow.status === "WAITING" && (
              <Button
                onClick={() => {
                  handleSendControlCommand(namespaceID, workflowName, "STOP_SOURCE_TRAFFIC_ACK");
                }}
                loading={isStartWorkflowLoading}
                disabled={isStartWorkflowLoading}
              >
                Acknowledge stop source traffic
              </Button>
            )}
            <Button
              iconName="refresh"
              onClick={() => fetchWorkflow(namespaceID, workflowName)}
              loading={loading}
              ariaLabel="Refresh workflow details"
            />
          </SpaceBetween>
        </div>
        <Header variant="h1">
          {workflow.workflowName}
        </Header>

        <ColumnLayout columns={2} variant="text-grid">
          <SpaceBetween size="l">
            <div>
              <Box variant="awsui-key-label">Workflow Name</Box>
              <div>{workflow.workflowName}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Status</Box>
              <StatusIndicator type={getStatusIndicator(workflow.status)}>
                {workflow.status}
              </StatusIndicator>
            </div>
          </SpaceBetween>

          <SpaceBetween size="l">
            <div>
              <Box variant="awsui-key-label">Namespace ID</Box>
              <div>{workflow.namespaceID}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Source Bucket</Box>
              <div>{workflow.sourceBucketName}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Destination Bucket</Box>
              <div>{workflow.destBucketName}</div>
            </div>
          </SpaceBetween>
        </ColumnLayout>

        <Container header={<Header variant="h2">Source Configuration</Header>}>
          <ColumnLayout columns={2} variant="text-grid">
            <div>
              <Box variant="awsui-key-label">Source Role ARN</Box>
              <div>arn:aws:iam::{workflow.sourceAccountNumber}:role/s3a-bucket-permissions</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Source Account Number</Box>
              <div>{workflow.sourceAccountNumber}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Source Region</Box>
              <div>{workflow.sourceRegion}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Replication Time Control (RTC)</Box>
              <div>{workflow.runtimeConfig?.isReplicationTimeControlEnabled ? 'Enabled' : 'Disabled'}</div>
            </div>
          </ColumnLayout>
        </Container>

        <Container header={<Header variant="h2">Destination Configuration</Header>}>
          <ColumnLayout columns={2} variant="text-grid">
            <div>
              <Box variant="awsui-key-label">Destination Role ARN</Box>
              <div>arn:aws:iam::{workflow.destAccountNumber}:role/s3a-bucket-permissions</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Destination Account Number</Box>
              <div>{workflow.destAccountNumber}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Destination Region</Box>
              <div>{workflow.destRegion}</div>
            </div>
          </ColumnLayout>
        </Container>

        <Container header={<Header variant="h2">Workflow Timeline</Header>}>
          <ColumnLayout columns={2} variant="text-grid">
            <div>
              <Box variant="awsui-key-label">Created At</Box>
              <div>{workflow.createdAt || 'N/A'}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Started At</Box>
              <div>{workflow.startedAt || 'N/A'}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Backfill Completed At</Box>
              <div>{workflow.backfillCompletedAt || 'N/A'}</div>
            </div>

            <div>
              <Box variant="awsui-key-label">Completed At</Box>
              <div>{workflow.completedAt || 'N/A'}</div>
            </div>
          </ColumnLayout>
        </Container>
      </SpaceBetween>

      <Modal
        visible={isConfirmModalVisible}
        onDismiss={() => setIsConfirmModalVisible(false)}
        header="Start Workflow"
        closeAriaLabel="Close modal"
        footer={
          <Box float="right">
            <SpaceBetween direction="horizontal" size="xs">
              <Button
                variant="primary"
                onClick={() => {
                  setIsConfirmModalVisible(false);
                  handleStartWorkflow(namespaceID, workflowName, false);
                }}
              >
                Standard Workflow
              </Button>
              <Button
                variant="normal"
                onClick={() => {
                  setIsConfirmModalVisible(false);
                  handleStartWorkflow(namespaceID, workflowName, true);
                }}
              >
                Large Bucket Workflow
              </Button>
            </SpaceBetween>
          </Box>
        }
      >
        <p>Choose workflow type based on your bucket size:</p>
        <ul>
          <li>Standard: Less than 1B objects</li>
          <li>Large Bucket: More than 1B objects</li>
        </ul>
      </Modal>

    </Container>
  )
}

export default ViewWorkflow
