type KRAnyLike = Object | string | null;

export function parseObject(raw: string): Record<string, Object> {
  try {
    const value = JSON.parse(raw) as Object;
    if (value !== null && typeof value === 'object') {
      return value as Record<string, Object>;
    }
  } catch (error) {
  }
  return {};
}

export function asRecord(value: Object | null): Record<string, Object> {
  if (value !== null && typeof value === 'object') {
    return value as Record<string, Object>;
  }
  return {};
}

export function jsonString(obj: Record<string, Object>, key: string, fallback: string = ''): string {
  const value = obj[key];
  if (typeof value === 'string') {
    return value;
  }
  if (value == null) {
    return fallback;
  }
  return `${value}`;
}

export function jsonNumber(obj: Record<string, Object>, key: string, fallback: number = 0): number {
  const value = obj[key];
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? fallback : parsed;
  }
  return fallback;
}

export function jsonBool(obj: Record<string, Object>, key: string): boolean {
  const value = obj[key];
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value !== 0;
  }
  if (typeof value === 'string') {
    return value === 'true' || value === '1';
  }
  return false;
}

export function jsonObject(obj: Record<string, Object>, key: string): Record<string, Object> {
  const value = obj[key];
  if (value == null) {
    return {};
  }
  return asRecord(value as Object);
}

export function moduleParams(params: KRAnyLike): Record<string, Object> {
  if (params === null) {
    return {};
  }
  if (typeof params === 'string') {
    return parseObject(params);
  }
  return asRecord(params as Object);
}

export function mapOf(keys: string[], values: Object[]): Record<string, Object> {
  const map: Record<string, Object> = {};
  const count = Math.min(keys.length, values.length);
  for (let i = 0; i < count; i++) {
    map[keys[i]] = values[i];
  }
  return map;
}
