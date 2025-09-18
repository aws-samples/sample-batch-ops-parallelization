import { api } from '../api';
import { ENDPOINTS } from '../config';
import type { AxiosRequestConfig } from 'axios';
import type { GetWorkflowResponse, ListWorkflowsResponse, Workflow } from '../types';

/**
 * Workflow Service
 * Handles all API calls related to workflows
 */
export const workflowService = {
  /**
   * Get a workflow by ID
   * @param id - Workflow ID
   * @param config - Optional axios request config
   * @returns Promise with workflow data
   */
  getWorkflow: (namespaceID: string, workflowName: string, config?: AxiosRequestConfig): Promise<GetWorkflowResponse> => {
    return api.post<GetWorkflowResponse>(ENDPOINTS.getWorkflow, {
      workflowName,
      namespaceID
    } ,config);
  },

  /**
   * Create a new workflow
   * @param workflow - Workflow data
   * @param config - Optional axios request config
   * @returns Promise with void (200 status with empty body)
   */
  createWorkflow: (workflow: Workflow, config?: AxiosRequestConfig): Promise<void> => {
    return api.post<void>(ENDPOINTS.createWorkflow, { workflow }, config);
  },

  /**
   * List all workflows
   * @param config - Optional axios request config
   * @returns Promise with array of workflow responses
   */
  listWorkflows: (config?: AxiosRequestConfig): Promise<ListWorkflowsResponse> => {
    return api.post<ListWorkflowsResponse>(ENDPOINTS.listWorkflows, undefined, config);
  },

  /**
   * Start a workflow
   * @param namespaceID - Namespace ID of the workflow
   * @param workflowName - Name of the workflow
   * @param config - Optional axios request config
   * @returns Promise with void (200 status with empty body)
   */
  startWorkflow: (namespaceID: string, workflowName: string, config?: AxiosRequestConfig): Promise<void> => {
    return api.post<void>(ENDPOINTS.startWorkflow, {
      workflowName,
      namespaceID
    }, config);
  },

    /**
   * Start a manifest split workflow for large buckets (>1 billion objects)
   * @param namespaceID - Namespace ID of the workflow
   * @param workflowName - Name of the workflow
   * @param config - Optional axios request config
   * @returns Promise with void (200 status with empty body)
   */
  startManifestSplitWorkflow: (namespaceID: string, workflowName: string, config?: AxiosRequestConfig): Promise<void> => {
    return api.post<void>(ENDPOINTS.startManifestSplitWorkflow, {
      workflowName,
      namespaceID
    }, config);
  },

    /**
   * Send control command
   * @param namespaceID - Namespace ID of the workflow
   * @param workflowName - Name of the workflow
   * @param notificationID - NotificationID for customer acknowledgement
   * @param config - Optional axios request config
   * @returns Promise with void (200 status with empty body)
   */
  sendControlCommand: (namespaceID: string, workflowName: string, notificationID: string, config?: AxiosRequestConfig): Promise<void> => {
    return api.post<void>(ENDPOINTS.sendControlCommand, {
      workflowName,
      namespaceID,
      notificationID
    }, config);
  }
};

export default workflowService;
