import React from "react";
import ReactDOM from "react-dom";
import { App } from "./App";
import {initializeSentry} from "./common/sentry";

initializeSentry()

ReactDOM.render(
    <App />,
    document.getElementById("root")
);