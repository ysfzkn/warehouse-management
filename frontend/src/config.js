/**
 * Application Configuration
 * Changes in this file are automatically applied via hot reload
 */

const config = {
  // API Base URL
  api: {
    baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
    timeout: 30000,
  },

  // App Settings
  app: {
    name: 'Depo Yönetim Sistemi',
    version: '1.0.0',
    environment: process.env.NODE_ENV || 'development',
  },

  // Feature Flags (changing these values triggers hot reload!)
  features: {
    enableDebugMode: true,
    enableAutoSave: true,
    showDevTools: true,
  },

  // UI Settings
  ui: {
    itemsPerPage: 10,
    dateFormat: 'DD/MM/YYYY',
    timeFormat: 'HH:mm',
  },
};

export default config;

