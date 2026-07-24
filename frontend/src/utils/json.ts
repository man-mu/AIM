import JSONbig from 'json-bigint';

const json = JSONbig({ storeAsString: true });

export function parseJsonResponse(data: unknown): unknown {
  if (typeof data !== 'string') {
    return data;
  }

  try {
    return json.parse(data) as unknown;
  } catch {
    return data;
  }
}
