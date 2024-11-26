import fetch from "jest-fetch-mock"
import {handler as lambdaHandler} from "../src/lambda";
import {lambdaTestData} from "./lambda-test-data";

beforeEach(() => {
  fetch.resetMocks();
  process.env.BASE_URL = "https://your-base-url"; // Mock base URL
  process.env.COINGECKO_API_KEY = "mock-api-key"; // Mock API key
});

describe("Lambda Function Tests", () => {
  it.each(
    (Object.keys(lambdaTestData) as (keyof typeof lambdaTestData)[]).map((testName: keyof typeof lambdaTestData) => [
      lambdaTestData[testName].httpMethod,
      lambdaTestData[testName].path,
      'queryStringParameters' in lambdaTestData[testName] ? lambdaTestData[testName].queryStringParameters : {},
      'pathParameters' in lambdaTestData[testName] ? lambdaTestData[testName].pathParameters : {},
      lambdaTestData[testName].expectedResponse,
      lambdaTestData[testName].mockedCoingeckoResponse,
    ])
  )(
    "should return the expected response for %s",
    async (
      httpMethod,
      path,
      queryStringParameters,
      pathParameters,
      expectedResponse,
      mockedCoingeckoResponse
    ) => {
      global.fetch = jest.fn(() =>
        Promise.resolve({
          json: () => mockedCoingeckoResponse,
          ok: true,
        } as unknown as Response)
      );
      const response = await lambdaHandler({
        httpMethod,
        path,
        queryStringParameters,
        pathParameters,
      });

      expect(response.statusCode).toEqual(expectedResponse);
    }
  );
});
