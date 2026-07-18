import type { AsrProvider, ProviderAsrRequest, ProviderAsrResponse } from './contracts.js';
import { NoterError } from '../errors.js';
import {
  createTimedSignal,
  fetchUpstream,
  mapUpstreamStatus,
  readJson,
  requireRecord,
} from './http.js';

export class CompatibleAsrProvider implements AsrProvider {
  public async transcribe(request: ProviderAsrRequest): Promise<ProviderAsrResponse> {
    const timedSignal = createTimedSignal(request.signal, request.config.timeoutMs);
    try {
      const upstreamResponse = await fetchUpstream(
        request.config.url,
        {
          method: 'POST',
          headers: {
            authorization: `Bearer ${request.config.apiKey}`,
            'content-type': 'application/json',
          },
          body: JSON.stringify({
            model: request.config.model,
            ...(request.language ? { language: request.language } : {}),
            input_audio: {
              data: request.audio.toString('base64'),
              format: 'm4a',
            },
          }),
        },
        timedSignal,
      );

      if (!upstreamResponse.ok) {
        await upstreamResponse.arrayBuffer();
        throw mapUpstreamStatus(upstreamResponse.status);
      }
      const payload = requireRecord(await readJson(upstreamResponse));
      if (typeof payload.text !== 'string' || payload.text.trim().length === 0) {
        throw new NoterError('invalid_upstream_response');
      }
      return { text: payload.text.trim(), upstreamStatus: upstreamResponse.status };
    } finally {
      timedSignal.dispose();
    }
  }
}
