import fetch from "jest-fetch-mock";
import { handler as lambdaHandler } from "../src/lambda";
import { lambdaTestData } from "./lambda-test-data";
import {
  APIGatewayProxyEvent,
  APIGatewayProxyEventPathParameters,
  APIGatewayProxyEventQueryStringParameters,
} from "aws-lambda";

beforeEach(() => {
  fetch.resetMocks();
  process.env.COINGECKO_BASE_URL = "https://coingecko.base.url"; // Mock base URL
  process.env.COINGECKO_API_KEY = "mock-api-key"; // Mock API key
  process.env.VECHAIN_STATS_BASE_URL = "https://vechain-stats.base.url"; // Mock base URL
  process.env.VECHAIN_STATS_API_KEY = "mock-api-key"; // Mock API key
});

describe("Lambda Function Tests", () => {
  it.each(
    (Object.keys(lambdaTestData) as (keyof typeof lambdaTestData)[]).map(
      (testName: keyof typeof lambdaTestData) => [
        lambdaTestData[testName].httpMethod,
        lambdaTestData[testName].path,
        "queryStringParameters" in lambdaTestData[testName]
          ? lambdaTestData[testName].queryStringParameters
          : {},
        "pathParameters" in lambdaTestData[testName]
          ? lambdaTestData[testName].pathParameters
          : {},
        lambdaTestData[testName].expectedResponse,
        lambdaTestData[testName].mockedResponse,
      ]
    )
  )(
    "should return the expected response for %s",
    async (
      httpMethod,
      path,
      queryStringParameters,
      pathParameters,
      expectedResponse,
      mockedResponse
    ) => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          json: () => mockedResponse,
          ok: true,
        } as unknown as Response)
      );
      const response = await lambdaHandler({
        httpMethod,
        path,
        queryStringParameters:
          queryStringParameters as APIGatewayProxyEventQueryStringParameters | null,
        pathParameters:
          pathParameters as APIGatewayProxyEventPathParameters | null,
      } as APIGatewayProxyEvent);

      expect(response.statusCode).toEqual(expectedResponse);
    }
  );
});
