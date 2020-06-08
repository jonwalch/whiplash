import React from "react";
import ReactDOM from "react-dom";
import * as Sentry from '@sentry/browser';
import {TwitchExtension} from "./components/TwitchExtension";

if (process.env.NODE_ENV === 'production') {
    Sentry.init({dsn: "https://a46fc141c04b403a8c6972913e005522@o404694.ingest.sentry.io/5269185"});
}

ReactDOM.render(
    <TwitchExtension />,
    document.getElementById("root")
);
