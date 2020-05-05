import React, {useState, useEffect, useContext} from "react";
import { useInterval } from "../common";
import { getProp} from "../common/stream";
import {baseUrl} from "../config/const";

export function TwitchExtension(props: any) {
    const [proposition, setProposition] = useState<any>({});
    const [prevProposition, setPrevProposition] = useState<any>({});

    const getPropWrapper = (event:any) => {
        if (event["current-prop"]){
            setProposition(event["current-prop"]);
        } else {
            setProposition({});
        }
        if (event["previous-prop"]) {
            setPrevProposition(event["previous-prop"]);
        } else {
            setPrevProposition({});
        }
    };

    useEffect(() => {
        getProp().then((event) => {
            getPropWrapper(event)
        });
    }, []);

    useInterval(() => {
        getProp().then((event) => {
            getPropWrapper(event)
        });
    }, 3000);

    const renderPropositionText = () => {
        if (proposition["proposition/text"]) {
            return "Current Bet: " + proposition["proposition/text"];
        } else {
            return "Next proposition soon!";
        }
    };

    const renderContent = () => {
        return (
            // TODO: uninline styles
            <div style={{
                display: "flex",
                flexDirection: "row",
                justifyContent: "space-around",
                alignItems: "center",
                padding: "1rem 0 1rem 0"
            }}>
                <div>
                    <a href="https://whiplash.gg">
                        <img
                            src={baseUrl + "/img/logos/whiplash-horizontal-4c.svg"}
                            alt="Whiplash"
                            width="165"
                            height="36"
                            className="site-logo"
                        />
                    </a>
                    <div>Bet on <a href="https://whiplash.gg">whiplash.gg</a>!</div>
                </div>
                <div>{renderPropositionText()}</div>
            </div>
        );
    };

    return (
        <>
            {renderContent()}
        </>
    );
}
