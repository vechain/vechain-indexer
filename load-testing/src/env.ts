import { Options } from "k6/options";

// Default values
const DEFAULT_BASE_URL = "http://localhost:3000";
const DEFAULT_RAMP_UP_DURATION = "30s";
const DEFAULT_STAY_DURATION = "1m";
const DEFAULT_WIND_DOWN_DURATION = "30s";
const DEFAULT_TARGET_VUS = 1000;

// Function to get environment variable or use default value
const getEnv = (key: string, defaultValue: string): string => __ENV[key] || defaultValue;
const getEnvNumber = (key: string, defaultValue: number): number =>
    __ENV[key] ? parseInt(__ENV[key], 10) : defaultValue;

// Fetch values from environment variables
const rampUpDuration = getEnv("RAMP_UP_DURATION", DEFAULT_RAMP_UP_DURATION);
const stayDuration = getEnv("STAY_DURATION", DEFAULT_STAY_DURATION);
const windDownDuration = getEnv("WIND_DOWN_DURATION", DEFAULT_WIND_DOWN_DURATION);
const targetVUs = getEnvNumber("TARGET_VUS", DEFAULT_TARGET_VUS);

// Function that returns the k6 options
export const getOptions = (): Options => ({
  stages: [
    { duration: rampUpDuration, target: targetVUs }, // Ramp up
    { duration: stayDuration, target: targetVUs }, // Stay at peak load
    { duration: windDownDuration, target: 0 },  // Ramp-down
  ],
});


export default {
  BASE_URL: getEnv("BASE_URL", DEFAULT_BASE_URL),
  OPTIONS: getOptions()
}
