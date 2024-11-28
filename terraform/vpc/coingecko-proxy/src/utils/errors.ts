export const INTERNAL_SERVER_ERROR = {
  statusCode: 500,
  body: JSON.stringify({
    message: "Internal Server Error",
    error: "Error fetching price data",
  }),
};
