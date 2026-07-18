import { buildApp } from './app/app.js';
import { loadProcessConfig } from './config/config.js';

const config = loadProcessConfig();
const app = buildApp({ config, logger: { level: config.logLevel } });

try {
  await app.listen({ host: '0.0.0.0', port: 3000 });
} catch (error) {
  app.log.error({ err: error }, 'Noter API failed to start');
  process.exitCode = 1;
}
