import { ValidationSchemaType } from "../utils/types";

export const validationSchema: { [key: string]: ValidationSchemaType } = {
  "price-list": {
    rootType: "object",
    requiredFields: ["vet", "vtho"],
    types: {
      vet: "string",
      vtho: "string",
    },
  },
};
