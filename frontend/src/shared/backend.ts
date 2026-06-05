export const DEFAULT_BACKEND_PORT = 8089;
export const API_CONTEXT_PATH = '/api/v1';

export function getApiBaseUrl(port = DEFAULT_BACKEND_PORT): string {
  return `http://127.0.0.1:${port}${API_CONTEXT_PATH}`;
}

export function getBackendHealthUrl(port = DEFAULT_BACKEND_PORT): string {
  return `${getApiBaseUrl(port)}/actuator/health`;
}
