const AWS = require("aws-sdk");

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
  test("GET /coins/market-chart", async () => {
    const event = {
      httpMethod: "GET",
      path: "/coins/market-chart",
      pathParameters: { coin_id: "vechain" },
      queryStringParameters: { vs_currency: "usd", days: "30" },
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data.prices)).toBe(true); // Expect an array of market data
  });

  test("GET /simple/supported_vs_currencies", async () => {
    const event = {
      httpMethod: "GET",
      path: "/simple/supported_vs_currencies",
      queryStringParameters: null,
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of supported currencies
  });

  test("GET /coins/list", async () => {
    const event = {
      httpMethod: "GET",
      path: "/coins/list",
      queryStringParameters: { include_platform: "true" },
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of coins
  });

  test("GET /coins/markets", async () => {
    const event = {
      httpMethod: "GET",
      path: "/coins/markets",
      queryStringParameters: {
        vs_currency: "usd",
        order: "market_cap_desc",
        per_page: "10",
        page: "1",
        sparkline: "false",
      },
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(200);
    expect(response.body).toBeTruthy();
    const data = JSON.parse(response.body);
    expect(Array.isArray(data)).toBe(true); // Expect an array of market data
  });

  test("Unsupported path returns 404", async () => {
    const event = {
      httpMethod: "GET",
      path: "/unknown/path",
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(404);
    expect(JSON.parse(response.body)).toEqual({ message: "Not Found" });
  });

  test("Unsupported HTTP method returns 405", async () => {
    const event = {
      httpMethod: "POST",
      path: "/coins/markets",
    };

    const response = await invokeLambda(event);
    expect(response.statusCode).toBe(405);
    expect(JSON.parse(response.body)).toEqual({
      message: "Method Not Allowed",
    });
  });
});
