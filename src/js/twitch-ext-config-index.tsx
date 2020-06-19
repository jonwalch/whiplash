import React from "react";
import ReactDOM from "react-dom";
import {TwitchExtensionConfig} from "./components/TwitchExtensionConfig";
import {initializeSentry} from "./common/sentry";

initializeSentry()

ReactDOM.render(
    <TwitchExtensionConfig />,
    document.getElementById("root")
);
