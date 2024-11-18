const lambdaHandler = require("./lambda");
const fetch = require("jest-fetch-mock");
const aws_console_tests = require("./aws_console_tests.json");

beforeEach(() => {
  fetch.resetMocks();
  process.env.BASE_URL = "https://your-base-url"; // Mock base URL
  process.env.COINGECKO_API_KEY = "mock-api-key"; // Mock API key
});

describe("Lambda Function Tests", () => {
  it.each(
    Object.keys(aws_console_tests).map((testName) => [
      aws_console_tests[testName].path,
      aws_console_tests[testName].queryStringParameters,
      aws_console_tests[testName].pathParameters,
      aws_console_tests[testName].expectedResponse,
      aws_console_tests[testName].mockedCoingeckoResponse
    ])
  )("should return the expected response for %s", async (path, queryStringParameters, pathParameters, expectedResponse, mockedCoingeckoResponse) => {
    fetch.mockResponse(JSON.stringify(mockedCoingeckoResponse));
    const response = await lambdaHandler.handler({
      path,
      queryStringParameters,
      pathParameters,
    });

    expect(response.statusCode).toEqual(expectedResponse);
  });
  
});
