import express from 'express';
import serverless from 'serverless-http';
import { serve, setup } from 'swagger-ui-express';
import { readFile } from 'fs/promises';

const swagger = JSON.parse(
  await readFile(
    new URL(process.env.LAMBDA_TASK_ROOT + '/swagger.json', import.meta.url)
  )
);

let applicationPath = process.env.SWAGGER_UI_URL_PATH;
if (!applicationPath) {
  applicationPath = "/docs";
}
if (!applicationPath.startsWith('/')) {
  applicationPath = '/' + applicationPath;
}

const app = express();
app.use(
  applicationPath,
  serve,
  setup(null, {
    swaggerOptions: {
      spec: swagger
    }
  })
);

export const handler = serverless(app);
