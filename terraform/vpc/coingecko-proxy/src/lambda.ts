import { INTERNAL_SERVER_ERROR } from "./utils/errors";
import { getResponseData as getCoingeckoResponseData } from "./coingecko/query";
import { getResponseData as getVechainStatsResponseData } from "./vechain-stats/query";
import { APIGatewayProxyEvent, APIGatewayProxyResult } from "aws-lambda";
import { getSecretValues } from "./utils/secret-manager";

export const handler = async (
  event: APIGatewayProxyEvent
): Promise<APIGatewayProxyResult> => {
  if (
    !process.env.COINGECKO_BASE_URL ||
    !process.env.VECHAIN_STATS_BASE_URL
  ) {
    console.error("Missing environment variables");
    return INTERNAL_SERVER_ERROR;
  }

  await getSecretValues(["coingecko_api_key", "vechain_stats_api_key"]);

  const { httpMethod, pathParameters, path, queryStringParameters } = event;
  if (httpMethod !== "GET") {
    return {
      statusCode: 405,
      body: JSON.stringify({ message: "Method Not Allowed" }),
    };
  }

  if (path === "/price-list") {
    return getVechainStatsResponseData(
      `/token/price-list`,
      queryStringParameters,
      "price-list"
    );
  } else if (path === "/simple/supported_vs_currencies") {
    return getCoingeckoResponseData(
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
    return getCoingeckoResponseData(
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
    return getCoingeckoResponseData(
      `/coins/markets`,
      queryStringParameters,
      "markets"
    );
  } else if (/^\/coins\/[a-z-]+\/market_chart$/.test(path)) {
    if (!queryStringParameters) {
      return {
        statusCode: 400,
        body: JSON.stringify({ message: "Missing query parameters" }),
      };
    }
    return getCoingeckoResponseData(
      `/coins/${pathParameters!.coin_id}/market_chart`,
      queryStringParameters,
      "marketChart"
    );
  } else if (/^\/coins\/[a-z-]+$/.test(path)) {
    return getCoingeckoResponseData(
      `/coins/${pathParameters!.coin_id}`,
      {},
      "coins"
    );
  }

  return {
    statusCode: 404,
    body: JSON.stringify({ message: "Not Found" }),
  };
};
