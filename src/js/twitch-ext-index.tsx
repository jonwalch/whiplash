import React from "react";
import ReactDOM from "react-dom";
import {initializeSentry} from "./common/sentry";
import {TwitchExtApp} from "./TwitchExtApp";

initializeSentry()

ReactDOM.render(
    <TwitchExtApp />,
    document.getElementById("root")
);
