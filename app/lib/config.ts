const configuredApiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL?.trim();

// Keep local development immediately runnable, while making an omitted release
// endpoint visible to the UI instead of silently targeting a fake domain.
export const API_BASE_URL = (configuredApiBaseUrl || 'http://127.0.0.1:8091').replace(/\/$/, '');
export const apiConfigMissing = !configuredApiBaseUrl;

export const appConfig = {
  name: '聚芯节点',
  version: '0.1.0',
  requestTimeoutMs: 12_000,
};
