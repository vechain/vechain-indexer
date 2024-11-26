export const validationSchema = {
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
      requiredFields: ["asset_platform_id"],
      types: {
        asset_platform_id: "string",
      },
    },
    markets: {
      rootType: "array:object",
      requiredFields: [],
      types: {},
    },
  } as any;