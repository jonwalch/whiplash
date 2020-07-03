import * as Sentry from "@sentry/browser";

export const initializeSentry = () => {
    if (process.env.NODE_ENV === 'production') {
        Sentry.init({
            dsn: "https://a46fc141c04b403a8c6972913e005522@o404694.ingest.sentry.io/5269185",
            ignoreErrors: [
                // 'TypeError: Failed to fetch',
                'TypeError: NetworkError when attempting to fetch resource.',
                'TypeError: cancelled',
                'TypeError: Cancelled',
                'TypeError: The Internet connection appears to be offline.',
                'TypeError: The network connection was lost.',
                'AbortError: The operation was aborted.',
                'TypeError: La conexión de red se perdió.',
                'TypeError: The request timed out.',
            ],
        });
    }
}