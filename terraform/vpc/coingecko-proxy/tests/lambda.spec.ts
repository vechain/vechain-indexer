import fetch from "jest-fetch-mock"
import {handler as lambdaHandler} from "../src/lambda";
import {awsConsoleTests} from "./aws_console_tests";

beforeEach(() => {
  fetch.resetMocks();
  process.env.BASE_URL = "https://your-base-url"; // Mock base URL
  process.env.COINGECKO_API_KEY = "mock-api-key"; // Mock API key
});

describe("Lambda Function Tests", () => {
  it.each(
    Object.keys(awsConsoleTests).map((testName) => [
      awsConsoleTests[testName].httpMethod,
      awsConsoleTests[testName].path,
      awsConsoleTests[testName].queryStringParameters,
      awsConsoleTests[testName].pathParameters,
      awsConsoleTests[testName].expectedResponse,
      awsConsoleTests[testName].mockedCoingeckoResponse,
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
        } as Response)
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
