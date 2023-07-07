import {check} from "k6";
import http from "k6/http";
import accounts from "./data/fungibles-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";
import {DEFAULT_OPTIONS} from "./constants";

export const options = DEFAULT_OPTIONS

/**
 *  Make a GET request to the fungibles contracts endpoint using a random address
 */
export default () => {
    const account = randomElement(accounts);

    const fungiblesContracts = http.get(`${env.BASE_URL}/api/v1/fungibles/contracts?owner=${account}`);

    check(fungiblesContracts, {
        "status is 200": () => fungiblesContracts.status === 200,
        "has results": () => {
            if (typeof fungiblesContracts.body === "string") {
                const body = JSON.parse(fungiblesContracts.body);
                return body.length > 0;
            } else {
                return false;
            }
        },
    });
};
