export type ValidationSchemaType = {
    rootType: string;
    requiredFields?: string[];
    types?: {
      [key: string]: string;
    };
  };