import http from 'node:http';

const port = Number(process.env.PORT ?? '8787');

function send(response, status, body) {
  response.writeHead(status, { 'content-type': 'application/json' });
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

const server = http.createServer(async (request, response) => {
  try {
    if (request.method !== 'POST') {
      send(response, 404, { error: 'not found' });
      return;
    }

    if (request.url?.endsWith('/chat/completions')) {
      if (request.headers.authorization !== 'Bearer smoke-chat-key') {
        send(response, 401, { error: 'unauthorized' });
        return;
      }
      const body = await readJson(request);
      if (body.model !== 'smoke-chat-model' || !Array.isArray(body.messages)) {
        send(response, 422, { error: 'invalid request' });
        return;
      }
      send(response, 200, {
        choices: [
          {
            message: {
              role: 'assistant',
              content: null,
              tool_calls: [
                {
                  id: 'smoke-call-1',
                  type: 'function',
                  function: { name: 'create_alarm', arguments: '{}' },
                },
              ],
            },
          },
        ],
      });
      return;
    }

    if (request.url?.endsWith('/audio/transcriptions')) {
      if (request.headers.authorization !== 'Bearer smoke-asr-key') {
        send(response, 401, { error: 'unauthorized' });
        return;
      }
      const body = await readJson(request);
      if (
        body.model !== 'smoke-asr-model' ||
        typeof body.input_audio?.data !== 'string' ||
        body.input_audio.format !== 'm4a'
      ) {
        send(response, 422, { error: 'invalid request' });
        return;
      }
      send(response, 200, { text: 'wake me at eight' });
      return;
    }

    send(response, 404, { error: 'not found' });
  } catch {
    send(response, 400, { error: 'invalid request' });
  }
});

server.listen(port, '0.0.0.0');
