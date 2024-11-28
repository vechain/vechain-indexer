import { ValidationSchemaType } from "../utils/types";

export const validationSchema: { [key: string]: ValidationSchemaType } = {
  "price-list": {
    rootType: "object",
    requiredFields: ["vet", "vtho", "b3tr", "vot3"],
    types: {
      vet: "string",
      vtho: "string",
      b3tr: "string",
      vot3: "string",
    },
  },
};
