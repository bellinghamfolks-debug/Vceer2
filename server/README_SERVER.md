# Basir Gemini Proxy Server

This folder contains a sample Node.js proxy for Basir Android. It keeps the Gemini API key on the server instead of placing it in the APK.

Current server package version: **1.0.1**

## Endpoints

### `POST /api/basir`

Handles text questions, image analysis, translation, and writing tasks.

Example JSON body:

```json
{
  "task": "ask",
  "input": "What is shown on this page?",
  "instruction": "Answer clearly for a screen reader.",
  "language": "en",
  "image_base64": "optional base64 data",
  "mime_type": "image/jpeg"
}
```

### `POST /api/convert`

Accepts a PDF or PowerPoint file as multipart form data and returns a generated `.docx` file.

Fields:

- `file`
- `language`: `ar` or `en`
- `mode`: `full`, `simple`, `descriptions_only`, or `text_only`

## Setup

```bash
cd server
cp .env.example .env
npm install
npm start
```

Set at least:

- `GEMINI_API_KEY`
- `BASIR_APP_TOKEN`

In Basir Android, open **Settings**, then **Gemini setup**, choose proxy mode, enter the public HTTPS URL and matching token, and test the connection.

## Data handling

- Temporary uploaded conversion files are deleted by this sample server after the request finishes.
- The server does not intentionally create a user account or permanent content archive.
- Hosting-provider logs, network logs, crash logs, backups, and operational monitoring may still retain metadata or content depending on deployment configuration.
- The operator is responsible for HTTPS, access control, log settings, retention, deletion, breach handling, and a privacy notice that matches the deployed environment.
- Content forwarded to Google Gemini remains subject to Google’s terms and data-handling rules.

Do not describe an independently deployed proxy as a “Basir server” unless its operator, configuration, and policy are actually controlled by the Basir publisher.
