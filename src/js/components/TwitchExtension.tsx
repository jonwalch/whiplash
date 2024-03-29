import React, {useState, useEffect, useContext, useRef} from "react";
import { useInterval } from "../common";
import "../../../resources/public/css/App.css";
import {defaultLoggedIn, LoginContext} from "../contexts/LoginContext";
import {CORSGetUser, twitch, twitchBaseUrl, twitchOpaqueID} from "../TwitchExtApp";
import UIfx from 'uifx';
import {getUser} from "../common/getUser";

const kc = new UIfx(
    (process.env.NODE_ENV === 'development' ? 'http://localhost:3000/' : "https://whiplashesports.com/") + "dist/sfx/ka-ching.mp3",
    {
        volume: 0.4, // number between 0.0 ~ 1.0
        throttleMs: 100
    }
)

const startSound = new UIfx(
    (process.env.NODE_ENV === 'development' ? 'http://localhost:3000/' : "https://whiplashesports.com/") + "dist/sfx/swiftly.mp3",
    {
        volume: 0.4, // number between 0.0 ~ 1.0
        throttleMs: 100
    }
)

const failedToFetch = "failed to fetch";
const textStyles = {fontSize: ".75rem", padding: "0 0.5rem 0.5rem 0.5rem",}

export function TwitchExtension() {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const [proposition, setProposition] = useState<any>({});
    const [prevProposition, setPrevProposition] = useState<any>({});
    const [extClass, setExtClass] = useState<string>("twitch-extension slide-out");
    const [extPositionClass, setExtPositionClass] = useState<string>("");
    const [betAmount, setBetAmount] = useState<number>(0);
    const [betWaitingForResp, setBetWaitingForResp] = useState<boolean>(false);
    const [userTriedBetting, setUserTriedBetting] = useState<boolean>(false);
    const [sfxEnabled, setSfxEnabled] = useState<boolean>(false);

    const [channelID, setChannelId] = useState<string | null>(null);
    const [twitchOnAuthorized, setTwitchOnAuthorized] = useState<any>(null);

    // @ts-ignore
    const pulseP = useRef(null);

    useEffect(() => {
        const buttons = document.querySelectorAll('.button--vote');

        buttons.forEach((button) => {
            if (!toggleValid()) {
                // button text does match selection
                if (!button.classList.contains('is-active')) {
                    button.classList.add('is-active')
                }
            } else {
                // button text does not match selection
                button.classList.remove('is-active')
            }
        })
    }, [betAmount, loggedInState.cash, proposition["proposition/text"]]);

    //Play a ding if any notifications are a payout (this include cancels here)
    useEffect(() => {
        if (sfxEnabled && loggedInState.notifications.length > 0) {
            let i = 0;
            for (; i < loggedInState.notifications.length; i++) {
                // @ts-ignore
                if (loggedInState.notifications[i]["notification/type"] === "notification.type/payout") {
                    kc.play()
                    break;
                }
            }
        }
    }, [loggedInState.notifications])

    useEffect(() => {
        if (proposition["proposition/text"] && sfxEnabled) {
            startSound.play()
        }
    }, [proposition["proposition/text"]])

    const CORSMakePropBet = async (projectedResult : boolean) => {
        setBetWaitingForResp(true);
        setUserTriedBetting(true);
        const response = await fetch(twitchBaseUrl + "user/prop-bet/" + channelID, {
            headers: {
                "Content-Type": "application/json",
                "x-twitch-opaque-id": twitchOpaqueID(),
            },
            method: "POST",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
            body: JSON.stringify({
                projected_result: projectedResult,
                bet_amount: betAmount,
            })
        });

        // Trigger Google Analytics event
        // @ts-ignore
        ga(function(tracker) {
            const clientId = tracker.get('clientId');
            // @ts-ignore
            ga('send', 'event', 'Betting', 'ext-prop-bet', clientId, betAmount, {"dimension1": loggedInState.userName,})
        })

        setBetWaitingForResp(false);
        if (response.status === 200) {
            // update user's cash
            setLoggedInState(
                {uid: loggedInState.uid,
                    userName: loggedInState.userName,
                    status: loggedInState.status,
                    cash: loggedInState.cash !== -1 ? loggedInState.cash - betAmount : 500 - betAmount,
                    notifications: loggedInState.notifications,
                    "gated?": loggedInState["gated?"]})
        }
    };

    const getCORSProp = async () => {
        const response = await fetch(twitchBaseUrl + "stream/prop/" + channelID, {
            method: "GET",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
        });
        if (response.status === 200) {
            return await response.json();
        } else {
            return {}
        }
    };

    const getPropWrapper = (event:any) => {
        if (event["current-prop"]){
            setProposition(event["current-prop"]);
        } else {
            setProposition({});
        }
        if (event["previous-prop"]){
            setPrevProposition(event["previous-prop"]);
        } else {
            setPrevProposition({});
        }
    };

    const getTwitchUsername = (auth: any) => {
        fetch(twitchBaseUrl + "twitch/user-id-lookup", {
            headers: {
                "x-twitch-user-id": auth.channelId,
            },
            method: "GET",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
        }).then((response) => {
            if (response.status === 200) {
                response.json().then((resp) => {
                    setChannelId(resp.login)
                })
            } else {
                setChannelId(failedToFetch)
            }
        })
    }

    useEffect(() => {
        twitch.onAuthorized((auth: any) => {
            setTwitchOnAuthorized(auth)
            getTwitchUsername(auth)
        })

        if (channelID && channelID !== failedToFetch) {
            getCORSProp().then((event) => {
                getPropWrapper(event)
            });
        }

        twitch.configuration.onChanged(() => {
            let config = twitch.configuration.broadcaster ? twitch.configuration.broadcaster.content : "";

            try {
                config = JSON.parse(config)
            } catch (e) {
                config = ""
            }
            setExtPositionClass(config === "bottomleft" ? "is-bottom-left" : "");
        })
    }, []);

    useEffect( () => {
        if (Object.keys(proposition).length === 0 && proposition.constructor === Object) {
            setExtClass("twitch-extension slide-out " + extPositionClass)
        } else if (proposition["proposition/text"]) {
            setExtClass("twitch-extension slide-in " + extPositionClass)
        }
    }, [proposition])

    useEffect(() => {
        // @ts-ignore
        pulseP.current?.classList.remove("profit-pulse")
        if (userTriedBetting || loggedInState.userName) {
            CORSGetUser(loggedInState, setLoggedInState).then((delta) => {
                    if (delta > 0) {
                        // apply animation to show cash increase
                        // @ts-ignore
                        pulseP.current?.classList.add("profit-pulse")
                    }
                }
            );
        }
    }, [proposition["proposition/text"]]);

    useInterval(() => {
        if (channelID && channelID !== failedToFetch) {
            getCORSProp().then((event) => {
                getPropWrapper(event)
            });
        }
    }, 1000);

    useInterval(() => {
        if (channelID === failedToFetch && twitchOnAuthorized && twitchOnAuthorized.channelId) {
            getTwitchUsername(twitchOnAuthorized)
        }

        if (channelID !== failedToFetch) {
            getUser(setLoggedInState);
        }
    }, 5000);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const numbers = /^[0-9]*$/;
        if (numbers.test(e.target.value)) {
            const amount = parseInt(e.target.value, 10) || 0;
            setBetAmount(amount);
        }
    };

    const toggleValid = () => {
        if (loggedInState.status === "user.status/twitch-ext-unauth")
        {
            return betAmount === 0 ||
                betAmount > loggedInState.cash ||
                betWaitingForResp;

        } else {
            return betAmount === 0 ||
                // TODO: change this to a constant
                betAmount > 500 ||
                betWaitingForResp;
        }
    };

    const renderOutcomeText = (result: string) => {
        if (result === "proposition.result/true" ) {
            return "Yes";
        } else if (result === "proposition.result/false") {
            return "No";
        } else {
            return "Cancelled"
        }
    };

    const renderPropositionText = () => {
        if (proposition && proposition["proposition/text"]) {
            return (<p style={{margin:0}}>{proposition["proposition/text"]}</p>);
        } else if (prevProposition && prevProposition["proposition/text"]){
            return (
                <>
                    <p>{"Last proposition: " + prevProposition["proposition/text"]}</p>
                    <p style={{margin:0}}>
                        {"Outcome: " + renderOutcomeText(prevProposition["proposition/result"])}
                    </p>
                </>
            );
        } else {
            return <p>Next proposition soon!</p>;
        }
    };

    const renderBody = () => {
        if (channelID === null) {
            return (<div>Loading...</div>);
        } else if (channelID === failedToFetch) {
            return (<div>Something went wrong. Retrying...</div>);
        } else if (proposition && proposition["proposition/betting-seconds-left"] > 0 &&
            (loggedInState.cash >= 100 || loggedInState.cash === defaultLoggedIn.cash) &&
            !loggedInState["gated?"]) {
            return (
                <>
                    <p style={{...textStyles, ...{margin: 0}}}>
                        Timer: {proposition["proposition/betting-seconds-left"]}
                    </p>
                    <div className="form__group">
                        <input
                            style={{margin: "0.5rem"}}
                            className="form__input"
                            value={betAmount > 0 ? betAmount : ""}
                            onChange={e => handleInputChange(e)}
                            type="text"
                            name="betAmount"
                            id="betAmount"
                            autoComplete="off"
                            placeholder="W$100 minimum"
                        />
                    </div>
                    <div style={{display: "flex", marginBottom: 0, justifyContent: "space-evenly"}}>
                        <button
                            className="button button--vote "
                            style={{minWidth: "40%", margin: "0.25em", padding: "0.25rem"}}
                            type="button"
                            key="Yes"
                            disabled={toggleValid()}
                            onClick={() => {
                                CORSMakePropBet(true)
                            }}>
                            <div className={betWaitingForResp ? "loading" : ""}>
                                {betWaitingForResp ? "" : "Yes"}
                            </div>
                        </button>
                        <button
                            className="button button--vote"
                            style={{minWidth: "40%", margin: "0.25em", padding: "0.25rem"}}
                            type="button"
                            key="No"
                            disabled={toggleValid()}
                            onClick={() => {
                                CORSMakePropBet(false)
                            }}>
                            <div className={betWaitingForResp ? "loading" : ""}>
                                {betWaitingForResp ? "" : "No"}
                            </div>
                        </button>
                    </div>
                </>
            );
        } else if ((loggedInState.cash < 100 && loggedInState.cash !== defaultLoggedIn.cash) || loggedInState["gated?"]){
            return (
                <div style={{padding: "0 0.5rem 0.5rem 0.5rem"}}>Sign up on Whiplash.gg to keep playing!</div>
            );
        }
    }

    const renderContent = () => {
        // @ts-ignore
        return (
            // TODO: uninline styles
            <div className={extClass}>
                <span style={{display: "flex", justifyContent: "space-between"}}>
                    <img
                        src={twitchBaseUrl + "/img/logos/whiplash-horizontal-4c-gg.svg"}
                        alt="Whiplash"
                        width="165"
                        height="36"
                        style={{margin:"0.5rem", height: "auto", width: "60%"}}
                    />
                    <p style={{...textStyles, ...{ padding: "0.5rem", margin: 0, alignSelf: "center",}}}>
                    {/*Show 500 if cash is default. Cash gets set by getUser but only after someone has placed a bet.*/}
                        W$: <span ref={pulseP} style={{color: "gold"}}>{loggedInState.cash === -1 ? 500 : loggedInState.cash}</span>
                    </p>
                    <div>
                        <label style={{fontSize: "0.5rem"}}
                               htmlFor="sfxEnabled">SFX</label>
                        <input type="checkbox"
                               id="sfxEnabled"
                               checked={sfxEnabled}
                               onChange={() => {}}
                               onClick={() => {setSfxEnabled(!sfxEnabled)}}/>
                    </div>
                </span>
                <div style={{...textStyles, ...{fontSize: "1rem"}}}>
                    {renderPropositionText()}
                </div>
                {renderBody()}
            </div>
        );
    };

    return (
        <>
            {renderContent()}
        </>
    );
}
