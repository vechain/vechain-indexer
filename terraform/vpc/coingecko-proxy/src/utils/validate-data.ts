export const validateType = (value: any, expectedType: any) => {
  if (expectedType.startsWith("array:")) {
    if (!Array.isArray(value)) {
      return false;
    }
    const arrayType = expectedType.split(":")[1];
    return value.every((item) => typeof item === arrayType);
  }
  return typeof value === expectedType;
};

export const validateResponse = (data: any, schema: {rootType: any, requiredFields: any, types: any}) => {
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
};
