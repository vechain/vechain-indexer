import { validationSchema as coingeckoValidationSchema } from "./coingecko/query";
import { validateResponse } from "./utils/validate-data";

const INTERNAL_SERVER_ERROR = {
  statusCode: 500,
  body: JSON.stringify({
    message: "Internal Server Error",
    error: "Error fetching price data",
  }),
};

const getResponseData = async (route: any, queryStringParameters: any, validatorId: any) => {
  let queryParams = new URLSearchParams(queryStringParameters).toString();

  if (queryParams) {
    queryParams = `?${queryParams}`;
  }

  try {
    const response = await fetch(process.env.BASE_URL + route + queryParams, {
      headers: {
        accept: "application/json",
        "x-cg-demo-api-key": process.env.COINGECKO_API_KEY as string,
      },
    });

    const data = await response.json();

    if (!response.ok) {
      console.error("Coingecko returned error data:", data);
      throw new Error(`HTTP error! Status: ${response.status}`);
    }

    validateResponse(data, coingeckoValidationSchema[validatorId]);

    return {
      statusCode: 200,
      body: JSON.stringify(data),
    };
  } catch (error) {
    console.error("Error fetching data:", error);
    return INTERNAL_SERVER_ERROR;
  }
};

export const handler = async (event: any) => {
  if (!process.env.COINGECKO_API_KEY || !process.env.BASE_URL) {
    console.error("Missing environment variables");
    return INTERNAL_SERVER_ERROR;
  }
  const { httpMethod, pathParameters, path, queryStringParameters } = event;
  if (httpMethod !== "GET") {
    return {
      statusCode: 405,
      body: JSON.stringify({ message: "Method Not Allowed" }),
    };
  }

  if (path === "/simple/supported_vs_currencies") {
    return getResponseData(
      `/simple/supported_vs_currencies`,
      {},
      "supportedVsCurrencies"
    );
  } else if (path === "/coins/list") {
    if (!queryStringParameters) {
      return {
        statusCode: 400,
        body: JSON.stringify({ message: "Missing query parameters" }),
      };
    }
    return getResponseData(
      `/coins/list?include_platform=true`,
      queryStringParameters,
      "list"
    );
  } else if (path === "/coins/markets") {
    if (!queryStringParameters) {
      return {
        statusCode: 400,
        body: JSON.stringify({ message: "Missing query parameters" }),
      };
    }
    return getResponseData(`/coins/markets`, queryStringParameters, "markets");
  } else if (/^\/coins\/[a-z-]+\/market_chart$/.test(path)) {
    if (!queryStringParameters) {
      return {
        statusCode: 400,
        body: JSON.stringify({ message: "Missing query parameters" }),
      };
    }
    return getResponseData(
      `/coins/${pathParameters.coin_id}/market_chart`,
      queryStringParameters,
      "marketChart"
    );
  } else if (/^\/coins\/[a-z-]+$/.test(path)) {
    return getResponseData(`/coins/${pathParameters.coin_id}`, {}, "coins");
  }

  return {
    statusCode: 404,
    body: JSON.stringify({ message: "Not Found" }),
  };
};
