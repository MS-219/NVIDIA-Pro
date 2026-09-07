const DEFAULT_NODE_NAME = '聚芯节点';

// Device names come from the provisioning database and may contain a hardware
// model or serial number. Keep those implementation details out of the APP UI
// even when an older backend row still has them in its name field.
const HARDWARE_MODEL_PATTERN = /(?:\brk[\s-]?3588s?\b|\bnvidia\s+jetson\b|\bjetson\b|\borin\b|设备型号|型号\s*[:：]?)/i;

export function sanitizeNodeName(value: unknown): string {
  if (typeof value !== 'string') return DEFAULT_NODE_NAME;
  const name = value.trim().replace(/\s+/g, ' ');
  if (!name || HARDWARE_MODEL_PATTERN.test(name)) return DEFAULT_NODE_NAME;
  return name.slice(0, 80);
}
