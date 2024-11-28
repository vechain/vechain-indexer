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

  let coingecko_api_key = await (await getSecretValues([])).coingecko_api_key

  if (queryParams) {
    queryParams = `?${queryParams}`;
  }

  try {
    const response = await fetch(process.env.BASE_URL + route + queryParams, {
      headers: {
        accept: "application/json",
        "x-cg-demo-api-key": coingecko_api_key,
      },
    });

    const data = await response.json();

    if (!response.ok) {
      console.error("Coingecko returned error data:", data);
      throw new Error(`HTTP error! Status: ${response.status}`);
    }

    validateResponse(data, validationSchema[validatorId]);

    return {
      statusCode: 200,
      body: JSON.stringify(data),
    };
  } catch (error) {
    console.error("Error fetching Coingecko data:", error);
    return INTERNAL_SERVER_ERROR;
  }
};
