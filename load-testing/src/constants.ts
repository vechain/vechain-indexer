import { Options } from "k6/options";

export const DEFAULT_OPTIONS: Options = {
    stages: [
      { duration: "20s", target: 5 }, // simulate ramp-up of traffic from 1 to 60 users.
      { duration: "20s", target: 5 }, // stay at 60
      { duration: "20s", target: 10 }, // ramp-up to 100 users
      { duration: "20s", target: 10 }, // stay at 100 users for short amount of time
      { duration: "20s", target: 5 }, // ramp-down to 60 users
      { duration: "20s", target: 0 }, // ramp-down to 0 users
    ],
  };
