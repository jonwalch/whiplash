import React, {useState, useEffect, useContext, useRef} from "react";
import { useInterval } from "../common";
import {baseUrl} from "../config/const";
import UIfx from 'uifx';

const prompts = [
    {text:"Birdfood gets one or more headshot kills this round", end: 27, result: true},
    {text:"HackoruTV says 'STOOPID' before the next match starts", end: 72, result: true},
    {text:"Poison lands 10 or more lashes with her Whip", end: 114, result: false},
    {text:"Rillo says 'fuck' or 'fucking' this round", end: 148, result: true},
]

// @ts-ignore
import kaChing from '../sfx/ka-ching.mp3'

const kc = new UIfx(
    kaChing,
    {
        volume: 0.4, // number between 0.0 ~ 1.0
        throttleMs: 100
    }
)

export function Landing(props: any) {
    const [promptIndex, setPromptIndex] = useState<number>(0)
    const [prompt, setPrompt] = useState<string>(prompts[promptIndex]["text"]);
    const [pressed, setPressed] = useState<boolean>(false);
    const [secsLeftToBet, setSecsLeftToBet] = useState<number>(20)
    const [betAmount, setBetAmount] = useState<number>(100)
    const [secondsElapsed, setSecondsElapsed] = useState<number>(0)
    const [cash, setCash] = useState<number>(500)
    const [totalAmountBet, setTotalAmountBet] = useState<number>(0)
    const [sideBetOn, setSideBetOn] = useState<null | boolean>(null)

    // @ts-ignore
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
        if (sideBetOn === null && betAmount <= cash) {
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
    }, [pressed, betAmount]);

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
            setSecsLeftToBet(20)
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
        return pressed || betAmount > cash
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
                <h1 style={{display:"flex", justifyContent: "center", margin:"0"}}>
                    <img
                        src={baseUrl + "/img/logos/whiplash-horizontal-4c-gg.svg"}
                        alt="Whiplashgg"
                        width="165"
                        height="36"
                        className="site-logo__big"
                    />
                </h1>
                <h2 style={{textAlign: "center"}}>
                    Bet Points on Anything Live Streamed
                </h2>
                <div style={{margin: "0 25% 0.5rem 25%"}}>
                    <div style={{
                        position: "relative",
                        paddingBottom: "56.25%", /* 16:9 */
                        height: "0",
                        overflow: "hidden",}}>
                        <iframe
                            style={{position: "absolute", top: "0", left: "0", height: "100%", width: "100%"}}
                            width="800"
                            height="450"
                            src="https://www.youtube.com/embed/IwsSt4NZNRw?&autoplay=1&muted=1&modestbranding&rel=0"
                            frameBorder="0"
                            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
                            allowFullScreen>
                        </iframe>
                    </div>
                </div>
                <p style={{textAlign: "center"}}>Current Bet: {prompt}</p>
                <p style={{textAlign: "center", margin: "0"}}>W$: <span style={{color:"gold"}} ref={pulseP}>{cash}</span></p>
                <p style={{textAlign: "center", marginBottom: "0", fontSize: "0.75rem"}}>{betText()}</p>
                <div className="form__group"
                     style={{flexDirection: "row", justifyContent: "center"}}>
                    <input
                        style={{width: "20%"}}
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
                    <h3 style={{margin: "0"}}>Join our</h3>
                    <button
                        type="button"
                        style={{margin: "0 0.5rem"}}
                        className="button navigation__button__tiny"
                        onClick={() => {
                            const win = window.open("https://discord.gg/GsG2G9t", '_blank');
                            // @ts-ignore
                            win.focus();
                        }}>
                        <img src={baseUrl + "/img/logos/Discord-Logo-Wordmark-White.svg"}/>
                    </button>
                    <h3 style={{margin: "0"}}>to find out when we're live next.</h3>
                </div>
            </>
        );
    }

    return renderContent();
}
