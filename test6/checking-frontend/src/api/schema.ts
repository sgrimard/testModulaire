// Généré par tools/openapi-slice.mjs depuis ../app-core/build/openapi/openapi.json (/api/checking). Ne pas modifier.
export interface paths {
    "/api/checking/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["checkAll"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/checking/reports/{productId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["check"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/checking/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["events_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        CheckEvent: {
            type: string;
            /** Format: int64 */
            productId: number | null;
            report: components["schemas"]["CheckReport"] | null;
        };
        CheckReport: {
            /** Format: int64 */
            productId: number;
            product: string;
            /** Format: int32 */
            score: number;
            /** @enum {string} */
            status: "OK" | "WARNING" | "BLOCKING";
            metrics: components["schemas"]["Metric"][];
            findings: components["schemas"]["Finding"][];
            contributors: string[];
        };
        Finding: {
            rule: string;
            /** @enum {string} */
            severity: "INFO" | "WARNING" | "BLOCKING";
            message: string;
            /** Format: int32 */
            penalty: number;
        };
        Metric: {
            code: string;
            label: string;
            value: number;
            unit: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    checkAll: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CheckReport"][];
                };
            };
        };
    };
    check: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CheckReport"];
                };
            };
        };
    };
    events_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "text/event-stream": components["schemas"]["CheckEvent"][];
                };
            };
        };
    };
}
