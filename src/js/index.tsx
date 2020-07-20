import React from "react";
import ReactDOM from "react-dom";
import { App } from "./App";
import {initializeSentry} from "./common/sentry";
import * as FullStory from '@fullstory/browser';

FullStory.init({
    orgId: 'SVKBQ',
    devMode: process.env.NODE_ENV !== 'production',
});

initializeSentry()

ReactDOM.render(
    <App />,
    document.getElementById("root")
);