/**
 * Application configuration
 */

// API Gateway configuration
export const API_CONFIG = {
  // Base URL for API Gateway
  baseURL: 'https://ux424o6an6.execute-api.us-east-1.amazonaws.com/dev',
  
  // API version
  apiVersion: 'v1',
  
  // Default timeout in milliseconds
  timeout: 30000,
  
  // Default headers
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  }
};

// Endpoints
export const ENDPOINTS = {
  createWorkflow: '/createWorkflow',
  deleteWorkflow: '/deleteWorkflow',
  getWorkflow: '/getWorkflow',
  sendControlCommand: '/sendControlCommand',
  startWorkflow: '/startWorkflow',
  startManifestSplitWorkflow: '/startManifestSplitWorkflow',
  listWorkflows: '/listWorkflows'
};

export default {
  API_CONFIG,
  ENDPOINTS,
};
