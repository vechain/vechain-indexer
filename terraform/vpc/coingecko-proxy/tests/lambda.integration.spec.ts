import * as AWS from "aws-sdk"
import {awsConsoleTests} from "./aws_console_tests";

const lambda = new AWS.Lambda({
  region: process.env.AWS_REGION,
});

const invokeLambda = async (event) => {
  const params = {
    FunctionName: process.env.LAMBDA_FUNCTION_NAME,
    Payload: JSON.stringify(event),
  };

  try {
    const response = await lambda.invoke(params as AWS.Lambda.InvocationRequest).promise();
    return JSON.parse(response.Payload!.toString());
  } catch (error) {
    console.error("Error invoking Lambda:", error);
    throw error;
  }
};

// For integration test make sure that the lambda is running
// The code that you want to test
describe("Lambda Integration Tests", () => {
  test("GET /coins/{coin_id}", async () => {
    const response = await invokeLambda(awsConsoleTests.coins);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(data.asset_platform_id).toEqual("vechain");
  });

  test("GET /coins/{coin_id}/market_chart", async () => {
    const response = await invokeLambda(awsConsoleTests.marketChart);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data.prices)).toBe(true); // Expect an array of market data
  });

  test("GET /simple/supported_vs_currencies", async () => {
    const response = await invokeLambda(
      awsConsoleTests.supportedVsCurrencies
    );
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of supported currencies
  });

  test("GET /coins/list", async () => {
    const response = await invokeLambda(awsConsoleTests.list);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of coins
  });

  test("GET /coins/markets", async () => {
    const response = await invokeLambda(awsConsoleTests.markets);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of market data
  });

  test("Unsupported path returns 404", async () => {
    const response = await invokeLambda(awsConsoleTests.unknownPath);
    expect(response.statusCode).toBe(404);
    expect(JSON.parse(response.body)).toEqual({ message: "Not Found" });
  });

  test("Unsupported HTTP method returns 405", async () => {
    const response = await invokeLambda(
      awsConsoleTests.unsupportedHttpMethod
    );
    expect(response.statusCode).toBe(405);
    expect(JSON.parse(response.body)).toEqual({
      message: "Method Not Allowed",
    });
  });
});
