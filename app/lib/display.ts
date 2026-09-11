const DEFAULT_NODE_NAME = '';

// Device names come from the provisioning database and may contain a hardware
// model or serial number. Return empty when the name is unrecognisable so the
// UI can fall back to displaying the device code.
const HARDWARE_MODEL_PATTERN = /(?:\brk[\s-]?3588s?\b|\bnvidia\s+jetson\b|\bjetson\b|\borin\b|设备型号|型号\s*[:：]?)/i;

export function sanitizeNodeName(value: unknown): string {
  if (typeof value !== 'string') return DEFAULT_NODE_NAME;
  const name = value.trim().replace(/\s+/g, ' ');
  if (!name || HARDWARE_MODEL_PATTERN.test(name)) return DEFAULT_NODE_NAME;
  return name.slice(0, 80);
}
