import { describe, expect, it } from 'vitest';
import { parseJsonResponse } from './json';

describe('parseJsonResponse', () => {
  it('preserves unsafe integer identifiers as decimal strings', () => {
    const response = parseJsonResponse(
      '{"code":0,"message":"success","data":{"userId":1234567890123456789,"createdAt":1707100000000}}',
    ) as { data: { userId: string; createdAt: number } };

    expect(response.data.userId).toBe('1234567890123456789');
    expect(response.data.createdAt).toBe(1707100000000);
  });
});
