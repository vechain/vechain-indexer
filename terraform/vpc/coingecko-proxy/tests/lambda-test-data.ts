export const lambdaTestData = {
  priceList: {
    httpMethod: "GET",
    path: "/price-list",
    queryStringParameters: {
      vs_currency: "usd",
    },
    expectedResponse: 200,
    mockedResponse: {
      vet: "0.01602273",
      vtho: "0.00093",
      veusd: "1",
      sha: "0.00034201",
      b3tr: "0.05567417",
      vot3: "0.05567417"
    },
  },
  marketChart: {
    httpMethod: "GET",
    path: "/coins/vechain/market_chart",
    pathParameters: {
      coin_id: "vechain",
    },
    queryStringParameters: {
      vs_currency: "usd",
      days: "30",
    },
    expectedResponse: 200,
    mockedResponse: {
      prices: [
        [1620000000000, 0.1],
        [1620000000001, 0.2],
      ],
      market_caps: [
        [1620000000000, 100],
        [1620000000001, 200],
      ],
      total_volumes: [
        [1620000000000, 1000],
        [1620000000001, 2000],
      ],
    },
  },
  supportedVsCurrencies: {
    httpMethod: "GET",
    path: "/simple/supported_vs_currencies",
    expectedResponse: 200,
    mockedResponse: ["usd", "eur"],
  },
  list: {
    httpMethod: "GET",
    path: "/coins/list",
    queryStringParameters: {
      include_platform: "true",
    },
    expectedResponse: 200,
    mockedResponse: [
      {
        id: "vechain",
        symbol: "vet",
        name: "VeChain",
      },
    ],
  },
  coins: {
    httpMethod: "GET",
    path: "/coins/vechain",
    pathParameters: {
      coin_id: "vechain",
    },
    expectedResponse: 200,
    mockedResponse: {
      asset_platform_id: "vechain",
    },
  },
  markets: {
    httpMethod: "GET",
    path: "/coins/markets",
    queryStringParameters: {
      vs_currency: "usd",
      order: "market_cap_desc",
      per_page: "10",
      page: "1",
      sparkline: "false",
    },
    expectedResponse: 200,
    mockedResponse: [
      {
        id: "vechain",
        symbol: "vet",
        name: "VeChain",
        image:
          "https://assets.coingecko.com/coins/images/1167/large/VeChain-Logo-768x725.png",
        current_price: 0.1,
        market_cap: 100,
        total_volume: 1000,
        price_change_percentage_24h: 0.1,
      },
    ],
  },
  unknownPath: {
    httpMethod: "GET",
    path: "/unknown/path",
    pathParameters: null,
    queryStringParameters: null,
    expectedResponse: 404,
    mockedResponse: null,
  },
  unsupportedHttpMethod: {
    httpMethod: "POST",
    path: "/coins/markets",
    queryStringParameters: {
      vs_currency: "usd",
    },
    expectedResponse: 405,
    mockedResponse: null,
  },
};
