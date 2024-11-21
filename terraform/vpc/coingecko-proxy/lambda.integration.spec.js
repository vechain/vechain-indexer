const AWS = require("aws-sdk");
const aws_console_tests = require("./aws_console_tests.json");

const lambda = new AWS.Lambda({
  region: process.env.AWS_REGION,
});

const invokeLambda = async (event) => {
  const params = {
    FunctionName: process.env.LAMBDA_FUNCTION_NAME,
    Payload: JSON.stringify(event),
  };

  try {
    const response = await lambda.invoke(params).promise();
    return JSON.parse(response.Payload);
  } catch (error) {
    console.error("Error invoking Lambda:", error);
    throw error;
  }
};

// For integration test make sure that the lambda is running
// The code that you want to test
describe("Lambda Integration Tests", () => {
  test("GET /coins/{coin_id}", async () => {
    const response = await invokeLambda(aws_console_tests.coins);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(data.asset_platform_id).toEqual("vechain");
  });

  test("GET /coins/{coin_id}/market_chart", async () => {
    const response = await invokeLambda(aws_console_tests.marketChart);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data.prices)).toBe(true); // Expect an array of market data
  });

  test("GET /simple/supported_vs_currencies", async () => {
    const response = await invokeLambda(
      aws_console_tests.supportedVsCurrencies
    );
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of supported currencies
  });

  test("GET /coins/list", async () => {
    const response = await invokeLambda(aws_console_tests.list);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of coins
  });

  test("GET /coins/markets", async () => {
    const response = await invokeLambda(aws_console_tests.markets);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of market data
  });

  test("Unsupported path returns 404", async () => {
    const response = await invokeLambda(aws_console_tests.unknownPath);
    expect(response.statusCode).toBe(404);
    expect(JSON.parse(response.body)).toEqual({ message: "Not Found" });
  });

  test("Unsupported HTTP method returns 405", async () => {
    const response = await invokeLambda(
      aws_console_tests.unsupportedHttpMethod
    );
    expect(response.statusCode).toBe(405);
    expect(JSON.parse(response.body)).toEqual({
      message: "Method Not Allowed",
    });
  });
});
