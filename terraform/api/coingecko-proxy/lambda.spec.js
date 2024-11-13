const lambdaHandler = require("./lambda"); 
const fetch = require("jest-fetch-mock");

beforeEach(() => {
  fetch.resetMocks();
  process.env.BASE_URL = "https://your-base-url"; // Mock base URL
  process.env.COINGECKO_API_KEY = "mock-api-key"; // Mock API key
});

describe("Lambda Function Tests", () => {
  test("GET /coins/market-chart", async () => {
    fetch.mockResponseOnce(JSON.stringify({ data: "market chart data" }));

    const event = {
      httpMethod: "GET",
      path: "/coins/market-chart",
      pathParameters: { coin_id: "vechain" },
      queryStringParameters: { vs_currency: "usd", days: "30" },
    };

    const response = await lambdaHandler.handler(event);

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining(
        "/coins/bitcoin/market_chart?vs_currency=usd&days=30"
      ),
      expect.objectContaining({
        headers: expect.objectContaining({
          accept: "application/json",
          "x-cg-demo-api-key": process.env.COINGECKO_API_KEY,
        }),
      })
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body)).toEqual({ data: "market chart data" });
  });

  test("GET /simple/supported_vs_currencies", async () => {
    fetch.mockResponseOnce(JSON.stringify(["usd", "eur", "btc"]));

    const event = {
      httpMethod: "GET",
      path: "/simple/supported_vs_currencies",
      queryStringParameters: null,
    };

    const response = await lambdaHandler.handler(event);

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/simple/supported_vs_currencies"),
      expect.objectContaining({
        headers: expect.any(Object),
      })
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body)).toEqual(["usd", "eur", "btc"]);
  });

  test("GET /coins/token-endpoint", async () => {
    fetch.mockResponseOnce(JSON.stringify({ data: "token data" }));

    const event = {
      httpMethod: "GET",
      path: "/coins/token-endpoint",
      pathParameters: { coin_id: "ethereum" },
    };

    const response = await lambdaHandler.handler(event);

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/coins/vechain"),
      expect.any(Object)
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body)).toEqual({ data: "token data" });
  });

  test("GET /coins/list", async () => {
    fetch.mockResponseOnce(JSON.stringify([{ id: "vechain", symbol: "vet" }]));

    const event = {
      httpMethod: "GET",
      path: "/coins/list",
      queryStringParameters: { include_platform: "true" },
    };

    const response = await lambdaHandler.handler(event);

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/coins/list?include_platform=true"),
      expect.any(Object)
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body)).toEqual([
      { id: "bitcoin", symbol: "btc" },
    ]);
  });

  test("GET /coins/markets", async () => {
    fetch.mockResponseOnce(JSON.stringify([{ id: "vechain", price: 1 }]));

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

    const response = await lambdaHandler.handler(event);

    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining("/coins/markets?vs_currency=usd"),
      expect.any(Object)
    );

    expect(response.statusCode).toBe(200);
    expect(JSON.parse(response.body)).toEqual([
      { id: "vechain", price: 1 },
    ]);
  });

  test("Unsupported path returns 404", async () => {
    const event = {
      httpMethod: "GET",
      path: "/unknown/path",
    };

    const response = await lambdaHandler.handler(event);

    expect(response.statusCode).toBe(404);
    expect(JSON.parse(response.body)).toEqual({ message: "Not Found" });
  });

  test("Unsupported HTTP method returns 405", async () => {
    const event = {
      httpMethod: "POST",
      path: "/coins/markets",
    };

    const response = await lambdaHandler.handler(event);

    expect(response.statusCode).toBe(405);
    expect(JSON.parse(response.body)).toEqual({
      message: "Method Not Allowed",
    });
  });
});
