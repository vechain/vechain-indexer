exports.handler = async (event) => {
  if (!process.env.COINGECKO_API_KEY || !process.env.BASE_URL) {
    console.error("Missing environment variables");
    return {
      statusCode: 500,
      body: JSON.stringify({
        message: "Internal Server Error",
        error: "Error fetching price data",
      }),
    };
  }
  const { httpMethod, pathParameters, path, queryStringParameters } = event;
  if (httpMethod !== "GET") {
    return {
      statusCode: 405,
      body: JSON.stringify({ message: "Method Not Allowed" }),
    };
  }

  if (path === "/coins/market-chart") {
    return marketChart(pathParameters.coin_id, queryStringParameters);
  } else if (path === "/simple/supported_vs_currencies") {
    return supportedVsCurrencies();
  } else if (path === "/coins/token-endpoint") {
    return tokenEndpoint(pathParameters.coin_id);
  } else if (path === "/coins/list") {
    return list(queryStringParameters);
  } 
  else if (path === "/coins") {
    return coins(pathParameters.coin_id);
  }
  else if (path === "/coins/markets") {
    return markets(queryStringParameters);
  }

  return {
    statusCode: 404,
    body: JSON.stringify({ message: "Not Found" }),
  };
};

const getResponseData = async (route, queryStringParameters) => {

  let queryParams = new URLSearchParams(queryStringParameters).toString()

  if(queryParams) {
    queryParams = `?${queryParams}`
  }

  console.warn("queryParams", queryParams);
  try {
    const response = await fetch(process.env.BASE_URL + route + queryParams, {
      headers: {
        accept: "application/json",
        "x-cg-demo-api-key": process.env.COINGECKO_API_KEY,
      },
    });


    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`);
    }

    const data = await response.json();
    return {
      statusCode: 200,
      body: JSON.stringify(data),
    };
  } catch (error) {
    console.error("Error fetching data:", error);
    return {
      statusCode: 500,
      body: JSON.stringify({
        message: "Internal Server Error",
        error: "Error fetching price data",
      }),
    };
  }
};

const markets = async () => {
  return getResponseData(`/coins/markets`, queryStringParameters);
}

const coins = async (coin) => {
  return getResponseData(`/coins/${coin}`, {});
}

const marketChart = async (coin, queryStringParameters) => {
  return getResponseData(`/coins/${coin}/market_chart`, queryStringParameters);
};

const supportedVsCurrencies = async () => {
  return getResponseData(`/simple/supported_vs_currencies`, {});
};

const tokenEndpoint = async (coin) => {
  return getResponseData(
    `/coins/${coin}`, {}
  );
};

const list = async (queryStringParameters) => {
  return getResponseData(`/coins/list?include_platform=true`, queryStringParameters);
};