import React from "react";
import ReactDOM from "react-dom";
import {TwitchExtension} from "./components/TwitchExtension";
import {initializeSentry} from "./common/sentry";

initializeSentry()

ReactDOM.render(
    <TwitchExtension />,
    document.getElementById("root")
);
