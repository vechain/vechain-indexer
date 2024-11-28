import { ValidationSchemaType } from "../utils/types";

export const validationSchema: { [key: string]: ValidationSchemaType } = {
  supportedVsCurrencies: {
    rootType: "array:string",
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
  },
};
