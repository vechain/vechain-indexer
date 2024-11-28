import { SecretsManager } from "aws-sdk";

const secretsManager = new SecretsManager();

type Secrets = {
  vechain_stats_api_key: string;
  coingecko_api_key: string;
};

let cachedSecrets: Secrets | null = null;

export const getSecretValues = async (
  secretNames: string[]
): Promise<Secrets> => {
  if (cachedSecrets) {
    return cachedSecrets;
  }
  let res: { [key: string]: string } = {};

  for (let secretName of secretNames) {
    try {
      const data = await secretsManager
        .getSecretValue({ SecretId: secretName })
        .promise();
      if (secretName in data) {
        res[secretName] = data.SecretString as string;
      } 
      else {
        throw new Error(`Secret ${secretName} not found`);
      }
    } catch (err) {
      if (err instanceof Error) {
        console.error(`Error fetching secret: ${err.message}`);
      } else {
        console.error(`Error fetching secret: ${err}`);
      }
      throw err;
    }
  }

  return res as Secrets;
};
