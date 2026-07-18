import type { AsrProvider } from '../providers/contracts.js';
import type { NoterApiConfig } from '../config/config.js';

export class AsrService {
  public constructor(
    private readonly config: NoterApiConfig,
    private readonly provider: AsrProvider,
  ) {}

  public async transcribe(
    audio: Buffer,
    language: string | undefined,
    signal: AbortSignal,
    requestId: string,
  ): Promise<{ readonly requestId: string; readonly text: string }> {
    const result = await this.provider.transcribe({
      audio,
      ...(language ? { language } : {}),
      config: this.config.asr,
      signal,
    });
    return { requestId, text: result.text };
  }
}
