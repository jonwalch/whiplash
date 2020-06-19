import React from "react";
import ReactDOM from "react-dom";
import * as Sentry from '@sentry/browser';
import { App } from "./App";
import {initializeSentry} from "./common/sentry";

initializeSentry()

ReactDOM.render(
    <App />,
    document.getElementById("root")
);