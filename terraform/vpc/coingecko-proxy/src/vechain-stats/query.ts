import { APIGatewayProxyEventQueryStringParameters } from "aws-lambda";
import { INTERNAL_SERVER_ERROR } from "../utils/errors";
import { validateResponse } from "../utils/validate-data";
import { validationSchema } from "./validation-schema";
import { getSecretValues } from "../utils/secret-manager";

export const getResponseData = async (
  route: string,
  queryStringParameters: APIGatewayProxyEventQueryStringParameters | null,
  validatorId: keyof typeof validationSchema
) => {
  let queryParams = new URLSearchParams(
    queryStringParameters as unknown as URLSearchParams
  ).toString();
  
  let vechain_stats_api_key = await (await getSecretValues([])).vechain_stats_api_key

  if (queryParams) {
    queryParams = `?${queryParams}&VCS_API_KEY=${vechain_stats_api_key}`;
  } else {
    queryParams = `?VCS_API_KEY=${vechain_stats_api_key}`;
  }

  try {
    const response = await fetch(
      process.env.VECHAIN_STATS_BASE_URL + route + queryParams,
      {
        headers: {
          accept: "application/json",
        },
      }
    );

    const data = await response.json();

    if (!response.ok) {
      console.error("VechainStats returned error data:", data);
      throw new Error(`HTTP error! Status: ${response.status}`);
    }

    validateResponse(data, validationSchema[validatorId]);

    return {
      statusCode: 200,
      body: JSON.stringify(data),
    };
  } catch (error) {
    console.error("Error fetching VechainStats data:", error);
    return INTERNAL_SERVER_ERROR;
  }
};
