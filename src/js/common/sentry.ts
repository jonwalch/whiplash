import * as Sentry from "@sentry/browser";

export const initializeSentry = () => {
    if (process.env.NODE_ENV === 'production') {
        Sentry.init({
            dsn: "https://a46fc141c04b403a8c6972913e005522@o404694.ingest.sentry.io/5269185",
            ignoreErrors: [
                // 'TypeError: Failed to fetch',
                'TypeError: NetworkError when attempting to fetch resource.',
                'TypeError: Cancelled'
            ],
        });
    }
}