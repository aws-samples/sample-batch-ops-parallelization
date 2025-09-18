import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios';
import { API_CONFIG } from '../config/config';

/**
 * Creates and configures an Axios instance with default settings
 */
const createApiClient = (): AxiosInstance => {
  // Create axios instance with default config
  const apiClient = axios.create({
    baseURL: API_CONFIG.baseURL,
    timeout: API_CONFIG.timeout,
    headers: API_CONFIG.headers,
  });

  // Request interceptor
  apiClient.interceptors.request.use(
    (config) => {
      // You can modify the request config here
      // For example, add authentication tokens
      // const token = getAuthToken();
      // if (token) {
      //   config.headers.Authorization = `Bearer ${token}`;
      // }
      return config;
    },
    (error) => {
      return Promise.reject(error);
    }
  );

  // Response interceptor
  apiClient.interceptors.response.use(
    (response: AxiosResponse) => {
      // You can modify the response data here
      return response;
    },
    (error) => {
      // Handle errors globally
      if (error.response) {
        // The request was made and the server responded with a status code
        // that falls out of the range of 2xx
        console.error('API Error Response:', error.response.status, error.response.data);
        
        // Handle specific error codes
        switch (error.response.status) {
          case 401:
            // Handle unauthorized
            console.error('Unauthorized access');
            break;
          case 403:
            // Handle forbidden
            console.error('Forbidden access');
            break;
          case 404:
            // Handle not found
            console.error('Resource not found');
            break;
          case 500:
            // Handle server error
            console.error('Internal server error');
            break;
          default:
            // Handle other errors
            break;
        }
      } else if (error.request) {
        // The request was made but no response was received
        console.error('API Request Error:', error.request);
      } else {
        // Something happened in setting up the request that triggered an Error
        console.error('API Error:', error.message);
      }
      
      return Promise.reject(error);
    }
  );

  return apiClient;
};

// Create default API client instance
const apiClient = createApiClient();

/**
 * API request methods
 */
export const api = {
  /**
   * GET request
   * @param url - The URL to request
   * @param config - Optional Axios request config
   */
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return apiClient.get(url, config).then((response) => response.data);
  },

  /**
   * POST request
   * @param url - The URL to request
   * @param data - The data to send
   * @param config - Optional Axios request config
   */
  post: <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return apiClient.post(url, data, config).then((response) => response.data);
  },

  /**
   * PUT request
   * @param url - The URL to request
   * @param data - The data to send
   * @param config - Optional Axios request config
   */
  put: <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return apiClient.put(url, data, config).then((response) => response.data);
  },

  /**
   * PATCH request
   * @param url - The URL to request
   * @param data - The data to send
   * @param config - Optional Axios request config
   */
  patch: <T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> => {
    return apiClient.patch(url, data, config).then((response) => response.data);
  },

  /**
   * DELETE request
   * @param url - The URL to request
   * @param config - Optional Axios request config
   */
  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<T> => {
    return apiClient.delete(url, config).then((response) => response.data);
  },
};

// Export the API client instance for direct use if needed
export default apiClient;
