const INTERNAL_SERVER_ERROR = {
  statusCode: 500,
  body: JSON.stringify({
    message: "Internal Server Error",
    error: "Error fetching price data",
  }),
};

const validationSchema = {
  supportedVsCurrencies: {
    rootType: "array:string",
    requiredFields: [],
  },
  marketChart: {
    rootType: "object",
    requiredFields: ["prices"],
    types: {
      prices: "object",
    },
  },
  list: {
    rootType: "array:object",
    requiredFields: [],
    types: {},
  },
  coins: {
    rootType: "object",
    requiredFields: ["id", "name", "symbol"],
    types: {
      id: "string",
      name: "string",
      symbol: "string",
    },
  },
  markets: {
    rootType: "array:object",
    requiredFields: [],
    types: {},
  },
};

function validateType(value, expectedType) {
  if (expectedType.startsWith("array:")) {
    if (!Array.isArray(value)) {
      return false;
    }
    const arrayType = expectedType.split(":")[1];
    return value.every((item) => typeof item === arrayType);
  }
  return typeof value === expectedType;
}

function validateResponse(data, schema) {
  const { rootType, requiredFields, types } = schema;

  if (rootType.startsWith("array:")) {
    if (!validateType(data, rootType)) {
      throw new Error(
        `Response validation error. Invalid root type. Expected '${rootType}', got '${typeof data}'`
      );
    }
    return true; // Root array validation passed
  }

  if (
    rootType !== "object" ||
    typeof data !== "object" ||
    Array.isArray(data)
  ) {
    throw new Error(
      `Response validation error. Invalid root type. Expected '${rootType}', got '${typeof data}'`
    );
  }

  for (const field of requiredFields || []) {
    if (!(field in data)) {
      throw new Error(
        `Response validation error. Missing required field: ${field}`
      );
    }

    const expectedType = types[field];
    if (!validateType(data[field], expectedType)) {
      throw new Error(
        `Response validation error. Invalid type for field '${field}'. Expected '${expectedType}', got '${typeof data[
          field
        ]}'`
      );
    }
  }
  return true; // Validation passed
}


const getResponseData = async (route, queryStringParameters, validatorId) => {
  let queryParams = new URLSearchParams(queryStringParameters).toString();
  
  if (queryParams) {
    queryParams = `?${queryParams}`;
  }
  
  try {
    const response = await fetch(process.env.BASE_URL + route + queryParams, {
      headers: {
        accept: "application/json",
        "x-cg-demo-api-key": process.env.COINGECKO_API_KEY,
      },
    });
    
    const data = await response.json();
    
    if (!response.ok) {
      console.error("Coingecko returned error data:", data);
      throw new Error(`HTTP error! Status: ${response.status}`);
    }
    
    validateResponse(data, validationSchema[validatorId]);
    
    return {
      statusCode: 200,
      body: JSON.stringify(data),
    };
  } catch (error) {
    console.error("Error fetching data:", error);
    return INTERNAL_SERVER_ERROR;
  }
};

exports.handler = async (event) => {
  if (!process.env.COINGECKO_API_KEY || !process.env.BASE_URL) {
    console.error("Missing environment variables");
    return INTERNAL_SERVER_ERROR;
  }
  const { httpMethod, pathParameters, path, queryStringParameters } = event;
  if (httpMethod !== "GET") {
    return {
      statusCode: 405,
      body: JSON.stringify({ message: "Method Not Allowed" }),
    };
  }

  if (path === "/simple/supported_vs_currencies") {
    return getResponseData(
      `/simple/supported_vs_currencies`,
      {},
      "supportedVsCurrencies"
    );
  } else if (/^\/coins\/[a-z-]+\/market_chart$/.test(path)) {
    return getResponseData(
      `/coins/${pathParameters.coin_id}/market_chart`,
      queryStringParameters,
      "marketChart"
    );
  } else if (path === "/coins/list") {
    return getResponseData(
      `/coins/list?include_platform=true`,
      queryStringParameters,
      "list"
    );
  } else if (/^\/coins\/[a-z-]+$/.test(path)) {
    return getResponseData(`/coins/${pathParameters.coin_id}`, {}, "coins");
  } else if (path === "/coins/markets") {
    return getResponseData(`/coins/markets`, queryStringParameters, "markets");
  }

  return {
    statusCode: 404,
    body: JSON.stringify({ message: "Not Found" }),
  };
};