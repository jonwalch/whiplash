import React, {useState, useEffect, useContext, useRef} from "react";
import {scrollToTop, useInterval} from "../common";
import UIfx from 'uifx';

const { gtag } = require('ga-gtag');

const prompts = [
    {text:"Bledsoe scores the next basket", end: 15, result: true},
    {text:"Next basket is an alley-oop", end: 30, result: true},
    {text:"Ball passes the ball before the Pelicans score", end: 38, result: true},
    {text:"Lebron scores the next basket", end: 48, result: false},
]

// @ts-ignore
import kaChing from '../sfx/ka-ching.mp3'
import {Footer} from "./Footer";
import {Header} from "./Header";
import {HeaderContext} from "../contexts/HeaderContext";
import {getEvent} from "../common/stream";
import {baseUrl} from "../config/const";

const kc = new UIfx(
    kaChing,
    {
        volume: 0.4, // number between 0.0 ~ 1.0
        throttleMs: 100
    }
)

const betSecs = 8;

export function NBALanding(props: any) {
    const { headerState, setHeaderState } = useContext(HeaderContext);
    const [promptIndex, setPromptIndex] = useState<number>(0)
    const [prompt, setPrompt] = useState<string>(prompts[promptIndex].text);
    const [pressed, setPressed] = useState<boolean>(false);
    const [secsLeftToBet, setSecsLeftToBet] = useState<number>(betSecs)
    const [betAmount, setBetAmount] = useState<number>(100)
    const [secondsElapsed, setSecondsElapsed] = useState<number>(0)
    const [cash, setCash] = useState<number>(500)
    const [totalAmountBet, setTotalAmountBet] = useState<number>(0)
    const [sideBetOn, setSideBetOn] = useState<null | boolean>(null)

    const pulseP = useRef(null);
    const yesButton = useRef(null);
    const noButton = useRef(null);

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const numbers = /^[0-9]*$/;
        if (numbers.test(e.target.value)) {
            const amount = parseInt(e.target.value, 10) || 0;
            setBetAmount(amount);
        }
    };

    useEffect(() => {
        if (secsLeftToBet < 0 && sideBetOn === null) {
            // @ts-ignore
            yesButton.current?.classList.remove("is-active")
            // @ts-ignore
            noButton.current?.classList.remove("is-active")
        } else if (sideBetOn === null && betAmount <= cash) {
            // @ts-ignore
            yesButton.current?.classList.add("is-active")
            // @ts-ignore
            noButton.current?.classList.add("is-active")
        } else if (sideBetOn) {
            // @ts-ignore
            yesButton.current?.classList.add("is-active")
            // @ts-ignore
            noButton.current?.classList.remove("is-active")
        } else if (sideBetOn === false) {
            // @ts-ignore
            noButton.current?.classList.add("is-active")
            // @ts-ignore
            yesButton.current?.classList.remove("is-active")
        } else {
            // @ts-ignore
            yesButton.current?.classList.remove("is-active")
            // @ts-ignore
            noButton.current?.classList.remove("is-active")
        }
    }, [pressed, betAmount, secsLeftToBet]);

    // redirect when there's an event
    useEffect(() => {
        getEvent().then((event) => {
            if (event["event/stream-source"] === "event.stream-source/none") {
                window.location.href = baseUrl
            }
        })
    },[])

    // redirect when there's an event
    useInterval(() => {
        getEvent().then((event) => {
            if (event["event/stream-source"] === "event.stream-source/none") {
                window.location.href = baseUrl
            }
        })
    }, 10000);

    const resolveBet = (currentPrompt:any) => {
        if (sideBetOn === currentPrompt.result) {
            setCash(cash + (totalAmountBet * 2))

            // @ts-ignore
            pulseP.current?.classList.add("profit-pulse")
            kc.play()

        } else if (cash === 0) {
            setCash(100)
        }
        setSideBetOn(null)
    }

    useEffect( () => {
        // @ts-ignore
        pulseP.current?.classList.remove("profit-pulse")
        setSecsLeftToBet(secsLeftToBet - 1)

        if (secsLeftToBet <= 0) {
            setPressed(true)
        }

        const currentPrompt = prompts[promptIndex]
        let nextPrompt = null;

        if (promptIndex + 1 < prompts.length) {
            nextPrompt = prompts[promptIndex + 1]
        }

        if (nextPrompt && secondsElapsed >= currentPrompt.end) {
            setPromptIndex(promptIndex + 1)
            setPrompt(nextPrompt.text)
            setSecsLeftToBet(betSecs)
            setPressed(false)

            resolveBet(currentPrompt)

            // @ts-ignore
        } else if (!nextPrompt && (promptIndex + 1) === prompts.length && secondsElapsed >= currentPrompt.end){
            resolveBet(currentPrompt)
        }

    }, [secondsElapsed])

    useInterval(() => {
        setSecondsElapsed(secondsElapsed + 1)
    }, 1000);

    const betDisabled = () => {
        return secsLeftToBet < 0 || pressed || betAmount > cash
    }

    const betText = () => {
        if (secsLeftToBet >= 0) {
            return "Timer: " + secsLeftToBet
        } else {
            return "Betting locked"
        }
    }

    const renderContent = () => {
        return (
            <>
                <h2 style={{textAlign: "center", margin: "0.5em 0 0.5em"}}>
                    Bet Points on Anything While Watching the NBA
                </h2>
                {/*TODO: media query with no margins on the right and left side*/}
                <div className="landing__video-container">
                    <div style={{
                        position: "relative",
                        paddingBottom: "56.25%", /* 16:9 */
                        height: "0",
                        overflow: "hidden",}}>
                        <iframe
                            style={{position: "absolute", top: "0", left: "0", height: "100%", width: "100%"}}
                            width="800"
                            height="450"
                            src="https://www.youtube.com/embed/K6F_wM273W0?start=597&autoplay=1&muted=1&modestbranding&rel=0"
                            frameBorder="0"
                            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
                            allowFullScreen>
                        </iframe>
                    </div>
                </div>
                <p style={{textAlign: "center", margin: "0 1em 1em 1em"}}>Current Bet: {prompt}</p>
                <p style={{textAlign: "center", margin: "0"}}>W$: <span style={{color:"gold"}} ref={pulseP}>{cash}</span></p>
                <p style={{textAlign: "center", marginBottom: "0", fontSize: "0.75rem"}}>{betText()}</p>
                <div className="form__group"
                     style={{flexDirection: "row", justifyContent: "center"}}>
                    <input
                        style={{width: "35%"}}
                        className="form__input"
                        value={betAmount > 0 ? betAmount : ""}
                        onChange={e => handleInputChange(e)}
                        type="text"
                        name="betAmount"
                        id="betAmount"
                        autoComplete="off"
                        placeholder="Bet Amount"
                    />
                </div>
                <div style={{display: "flex", margin: "1rem", justifyContent: "center"}}>
                    <button
                        ref={yesButton}
                        className="button button--vote "
                        style={{margin: "0.5rem 0.5rem 0 0.5rem"}}
                        type="button"
                        key="Yes"
                        id="yesButton"
                        disabled={betDisabled()}
                        onClick={() => {
                            setPressed(true)
                            setTotalAmountBet(totalAmountBet + betAmount)
                            setCash(cash - betAmount)
                            setSideBetOn(true)
                        }}>
                        Yes
                    </button>
                    <button
                        ref={noButton}
                        className="button button--vote"
                        style={{margin: "0.5rem 0.5rem 0 0.5rem"}}
                        type="button"
                        key="No"
                        id="noButton"
                        disabled={betDisabled()}
                        onClick={() => {
                            setPressed(true)
                            setTotalAmountBet(totalAmountBet + betAmount)
                            setCash(cash - betAmount)
                            setSideBetOn(false)
                        }}>
                        No
                    </button>
                </div>
                <div style={{display: "flex", justifyContent: "center", margin: "1.5rem", alignItems: "center"}}>
                    <button
                        style={{marginRight: "0.75rem"}}
                        type="button"
                        className="button"
                        onClick={() => {
                            scrollToTop();
                            setHeaderState({showLogin: false, showSignup: !headerState.showSignup})
                            gtag('event', 'nba-open-sign-up-form', {
                                event_category: 'Sign Up',
                            });
                        }}>
                        Sign Up
                    </button>
                    <h3 style={{margin: "0"}}>to be ready for game time.</h3>
                </div>
            </>
        );
    }

    return (
        <>
            <Header/>
            {renderContent()}
            <Footer/>
        </>
    );
}
