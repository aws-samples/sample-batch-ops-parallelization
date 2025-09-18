import { useState, useEffect } from 'react';
import { workflowService } from '../services';
import type { WorkflowView } from '../types';

// Import Cloudscape components
import Table from '@cloudscape-design/components/table';
import Box from '@cloudscape-design/components/box';
import SpaceBetween from '@cloudscape-design/components/space-between';
import Button from '@cloudscape-design/components/button';
import ButtonDropdown from '@cloudscape-design/components/button-dropdown';
import Spinner from '@cloudscape-design/components/spinner';
import StatusIndicator from '@cloudscape-design/components/status-indicator';
import { getStatusIndicator, getStatusPopoverContent, getStatusPopoverHeader, toWorkflowViewFromWorkflow } from '../utils/utils';
import Alert from '@cloudscape-design/components/alert';
import type { FlashbarProps } from '@cloudscape-design/components/flashbar';
import Header from '@cloudscape-design/components/header';
import TextFilter from '@cloudscape-design/components/text-filter';
import { Popover } from '@cloudscape-design/components';

interface ListWorkflowsProps {
  onViewWorkflow: (namespaceID: string, workflowName: string) => void;
  addNotification?: (notification: FlashbarProps.MessageDefinition) => void;
  clearNotifications?: () => void;
}

/**
 * ListWorkflows component
 * Displays a table of workflows with detailed information including:
 * - Workflow Info (Name and Namespace ID)
 * - Source Details (Bucket, Account, Region)
 * - Destination Details (Bucket, Account, Region)
 * - Created At
 * - Started At
 * - Backfill Completed At
 * - Status
 * 
 * Features:
 * - Search bar for filtering workflows
 * - Sortable columns
 * - Resizable columns
 * - "View details" button in each row for easy access
 * - Actions dropdown for workflow operations (start workflow, acknowledge stop source traffic)
 */
const ListWorkflows: React.FC<ListWorkflowsProps> = ({ onViewWorkflow, addNotification }) => {
  // State for workflows data
  const [workflows, setWorkflows] = useState<WorkflowView[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [startingWorkflow, setStartingWorkflow] = useState<string | null>(null);
  const [startWorkflowError, setStartWorkflowError] = useState<string | null>(null);
  const [sendingControlCommand, setSendingControlCommand] = useState<string | null>(null);
  const [sendControlCommandError, setSendControlCommandError] = useState<string | null>(null);
  const [selectedItems, setSelectedItems] = useState<WorkflowView[]>([]);
  const [filteringText, setFilteringText] = useState('');
  const [filteredItems, setFilteredItems] = useState<WorkflowView[]>([]);
  
  // Load workflows on component mount
  useEffect(() => {
    fetchWorkflows();
  }, []);
  
  // Filter workflows when filtering text or workflows change
  useEffect(() => {
    if (!filteringText) {
      setFilteredItems(workflows);
      return;
    }
    
    const lowerCaseFilter = filteringText.toLowerCase();
    const filtered = workflows.filter(item => 
      item.workflowName.toLowerCase().includes(lowerCaseFilter) ||
      item.namespaceID.toLowerCase().includes(lowerCaseFilter) ||
      item.sourceBucketName.toLowerCase().includes(lowerCaseFilter) ||
      item.destBucketName.toLowerCase().includes(lowerCaseFilter) ||
      item.status.toLowerCase().includes(lowerCaseFilter) ||
      (item.createdAt && item.createdAt.toLowerCase().includes(lowerCaseFilter)) ||
      (item.startedAt && item.startedAt.toLowerCase().includes(lowerCaseFilter)) ||
      (item.backfillCompletedAt && item.backfillCompletedAt.toLowerCase().includes(lowerCaseFilter))
    );
    
    setFilteredItems(filtered);
  }, [filteringText, workflows]);
  
  const fetchWorkflows = async () => {
    try {
      setLoading(true);
      const response = await workflowService.listWorkflows();
      const workflows = response.workflows.map(toWorkflowViewFromWorkflow);
      setWorkflows(workflows);
      setFilteredItems(workflows);
      setError(null);
    } catch (err) {
      console.error('Error fetching workflows:', err);
      setError('Failed to load workflows. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

  // Handle view workflow details
  const handleViewWorkflow = (namespaceID: string, workflowName: string) => {
    onViewWorkflow(namespaceID, workflowName);
  };
  
  // Handle start workflow
  const handleStartWorkflow = async (namespaceID: string, workflowName: string) => {
    try {
      setStartingWorkflow(`${namespaceID}:${workflowName}`);
      setStartWorkflowError(null);
      await workflowService.startWorkflow(namespaceID, workflowName);
      
      // Update the workflow status in the local state
      setWorkflows(prevWorkflows => 
        prevWorkflows.map(workflow => 
          workflow.namespaceID === namespaceID && workflow.workflowName === workflowName
            ? { ...workflow, status: 'RUNNING' }
            : workflow
        )
      );
      
      // Show success notification
      if (addNotification) {
        addNotification({
          type: 'success',
          header: 'Success',
          content: 'Successfully started workflow!',
          id: `start-workflow-success-${namespaceID}-${workflowName}`,
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } catch (err) {
      console.error('Error starting workflow:', err);
      setStartWorkflowError(`Failed to start workflow ${workflowName}. Please try again later.`);
      
      // Show error notification
      if (addNotification) {
        addNotification({
          type: 'error',
          header: 'Error',
          content: `Failed to start workflow ${workflowName}. Please try again.`,
          id: `start-workflow-error-${namespaceID}-${workflowName}`,
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } finally {
      setStartingWorkflow(null);
    }
  };
  
  // Get action items for the dropdown based on selected workflow
  const getActionItems = () => {
    const items: any[] = [];
    
    // If no workflow is selected, return empty items array
    if (selectedItems.length === 0) {
      return items;
    }
    
    const selectedWorkflow = selectedItems[0];
    const isStarting = startingWorkflow === `${selectedWorkflow.namespaceID}:${selectedWorkflow.workflowName}`;
    const isSendingCommand = sendingControlCommand === `${selectedWorkflow.namespaceID}:${selectedWorkflow.workflowName}`;
    
    // Start Workflow action - only when status is READY
    if (selectedWorkflow.status === 'READY') {
      items.push({
        id: 'start-workflow',
        text: 'Start workflow',
        disabled: isStarting,
        onClick: () => {
          console.log('Start Workflow clicked for:', selectedWorkflow.workflowName);
          handleStartWorkflow(selectedWorkflow.namespaceID, selectedWorkflow.workflowName);
        }
      });
    }
    
    // Acknowledge stop source traffic - only when status is WAITING
    if (selectedWorkflow.status === 'WAITING') {
      items.push({
        id: 'acknowledge-stop',
        text: 'Acknowledge stop source traffic',
        disabled: isSendingCommand,
        onClick: () => {
          console.log('Acknowledge stop source traffic clicked for:', selectedWorkflow.workflowName);
          handleSendControlCommand(selectedWorkflow.namespaceID, selectedWorkflow.workflowName, "STOP_SOURCE_TRAFFIC_ACK");
        }
      });
    }
    
    return items;
  };
  
  // Handle send control command
  const handleSendControlCommand = async (namespaceID: string, workflowName: string, notificationID: string) => {
    try {
      setSendingControlCommand(`${namespaceID}:${workflowName}`);
      setSendControlCommandError(null);
      await workflowService.sendControlCommand(namespaceID, workflowName, notificationID);
      
      // Update the workflow status in the local state
      setWorkflows(prevWorkflows => 
        prevWorkflows.map(workflow => 
          workflow.namespaceID === namespaceID && workflow.workflowName === workflowName
            ? { ...workflow, status: 'RUNNING' }
            : workflow
        )
      );
      
      // Show success notification
      if (addNotification) {
        addNotification({
          type: 'success',
          header: 'Success',
          content: 'Successfully acknowledged stop source traffic!',
          id: `send-control-command-success-${namespaceID}-${workflowName}`,
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } catch (err) {
      console.error('Error sending control command:', err);
      setSendControlCommandError(`Failed to acknowledge stop source traffic for ${workflowName}. Please try again later.`);
      
      // Show error notification
      if (addNotification) {
        addNotification({
          type: 'error',
          header: 'Error',
          content: `Failed to acknowledge stop source traffic for ${workflowName}. Please try again.`,
          id: `send-control-command-error-${namespaceID}-${workflowName}`,
          dismissible: true,
          dismissLabel: 'Dismiss message'
        });
      }
    } finally {
      setSendingControlCommand(null);
    }
  };
  
  // Render loading state
  if (loading) {
    return (
      <Box textAlign="center" padding="l">
        <SpaceBetween direction="vertical" size="m">
          <Spinner size="large" />
          <Box variant="p">Loading workflows...</Box>
        </SpaceBetween>
      </Box>
    );
  }
  
  // Render error state
  if (error) {
    return (
      <Box textAlign="center" padding="l">
        <StatusIndicator type="error">{error}</StatusIndicator>
      </Box>
    );
  }
  
  return (
    <>
      {startWorkflowError && (
        <Alert type="error" dismissible onDismiss={() => setStartWorkflowError(null)}>
          {startWorkflowError}
        </Alert>
      )}
      {sendControlCommandError && (
        <Alert type="error" dismissible onDismiss={() => setSendControlCommandError(null)}>
          {sendControlCommandError}
        </Alert>
      )}
      <div style={{ width: '100%', overflowX: 'auto' }}>
        <Table
        variant="container"
        wrapLines={true}
        resizableColumns
        stickyHeader
        selectionType="single"
        selectedItems={selectedItems}
        onSelectionChange={({ detail }) => setSelectedItems(detail.selectedItems)}
        columnDefinitions={[
          {
            id: 'workflowInfo',
            header: 'Workflow Info',
            cell: (item: WorkflowView) => (
              <div>
                <div><strong>{item.workflowName}</strong></div>
                <div style={{ fontSize: '12px', color: '#666' }}>{item.namespaceID}</div>
              </div>
            ),
            sortingField: 'workflowName',
          },
          {
            id: 'sourceDetails',
            header: 'Source Details',
            cell: (item: WorkflowView) => (
              <div style={{ fontSize: '13px', lineHeight: '1.4' }}>
                <div><strong>Bucket:</strong> {item.sourceBucketName}</div>
                <div><strong>Account:</strong> {item.sourceAccountNumber}</div>
                <div><strong>Region:</strong> {item.sourceRegion}</div>
              </div>
            ),
          },
          {
            id: 'destDetails',
            header: 'Destination Details',
            cell: (item: WorkflowView) => (
              <div style={{ fontSize: '13px', lineHeight: '1.4' }}>
                <div><strong>Bucket:</strong> {item.destBucketName}</div>
                <div><strong>Account:</strong> {item.destAccountNumber}</div>
                <div><strong>Region:</strong> {item.destRegion}</div>
              </div>
            ),
          },
          {
            id: 'createdAt',
            header: 'Created At',
            cell: (item: WorkflowView) => item.createdAt || 'N/A',
            sortingField: 'createdAt',
          },
          {
            id: 'startedAt',
            header: 'Started At',
            cell: (item: WorkflowView) => item.startedAt || 'N/A',
            sortingField: 'startedAt',
          },
          {
            id: 'backfillCompletedAt',
            header: 'Backfill Completed At',
            cell: (item: WorkflowView) => item.backfillCompletedAt || 'N/A',
            sortingField: 'backfillCompletedAt',
          },
          {
            id: 'status',
            header: 'Status',
            cell: (item: WorkflowView) => {
              const StatusComponent = (
                <StatusIndicator type={getStatusIndicator(item.status)}>
                  {item.status}
                </StatusIndicator>
              );

              return item.status === 'WAITING' ? (
                <Popover content={getStatusPopoverContent(item.status)}
                  header={getStatusPopoverHeader(item.status)}>
                  {StatusComponent}
                </Popover>
              ) : StatusComponent;
            },
          },
          {
            id: 'actions',
            header: 'Actions',
            cell: (item: WorkflowView) => (
              <Button 
                variant="link"
                onClick={() => handleViewWorkflow(item.namespaceID, item.workflowName)}
              >
                View details
              </Button>
            ),
          },
        ]}
        items={filteredItems}
        loadingText="Loading workflows"
        empty={
          <Box textAlign="center" padding="l">
            <b>No workflows</b>
            <Box variant="p" padding={{ bottom: 's' }}>
              No workflows to display.
            </Box>
          </Box>
        }
        header={
          <Header
            variant="awsui-h1-sticky"
            actions={
              <SpaceBetween direction="horizontal" size="xs">
                <ButtonDropdown
                  items={getActionItems()}
                  ariaLabel="Actions"
                  onItemClick={({ detail }) => {
                    const selectedItem = getActionItems().find(item => item.id === detail.id);
                    if (selectedItem && selectedItem.onClick) {
                      selectedItem.onClick();
                    }
                  }}
                >
                  Actions
                </ButtonDropdown>
                <Button 
                  iconName="refresh" 
                  onClick={() => {
                    fetchWorkflows();
                  }}
                  ariaLabel="Refresh workflows"
                />
              </SpaceBetween>
            }
          >
            Workflows
          </Header>
        }
        filter={
          <TextFilter
            filteringText={filteringText}
            filteringPlaceholder="Find workflows"
            filteringAriaLabel="Filter workflows"
            onChange={({ detail }) => setFilteringText(detail.filteringText)}
          />
        }
        // Pagination can be added later if needed
        />
      </div>
    </>
  );
};

export default ListWorkflows;
