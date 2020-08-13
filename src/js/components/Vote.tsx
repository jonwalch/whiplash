import React, {useState, useEffect, useContext, useRef} from "react";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
const { gtag } = require('ga-gtag');

import UIfx from 'uifx';
// @ts-ignore
import betStart from '../sfx/swiftly.mp3'

const startSound = new UIfx(
    betStart,
    {
      volume: 0.4, // number between 0.0 ~ 1.0
      throttleMs: 100
    }
)

export function Vote (props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [betAmount, setBetAmount] = useState<number>(0);
  const [betWaitingForResp, setBetWaitingForResp] = useState<boolean>(false);
  const [lastSuccessfulBetSide, setLastSuccessfulBetSide] = useState<null | boolean>(null);

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
    }, [betAmount, loggedInState.cash, props.proposition["proposition/text"]]);

  useEffect(() => {
    if (props.proposition["proposition/text"] && props.sfx) {
      startSound.play()
    }
  }, [props.proposition["proposition/text"]])

  const makePropBet = async (projectedResult : boolean) => {
    setBetWaitingForResp(true);
    const response = await fetch(baseUrl + "user/prop-bet/" + props.channelID, {
      headers: {
        "Content-Type": "application/json",
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({
        projected_result: projectedResult,
        bet_amount: betAmount,
      })
    });

    // Trigger Google Analytics event
    gtag('event', 'prop-bet', {
      event_category: 'Betting',
      event_label: loggedInState.userName,
      value: betAmount
    });

    const resp = await response.json();
    setBetWaitingForResp(false);

    if (response.status === 200) {
      setLastSuccessfulBetSide(projectedResult);
      const timer = setTimeout(() => {
        setLastSuccessfulBetSide(null)
      }, 1500);

      setLoggedInState(
          {
            uid: loggedInState.uid,
            userName: loggedInState.userName,
            status: loggedInState.status,
            cash: loggedInState.cash - betAmount,
            notifications: loggedInState.notifications,
            "gated?": loggedInState["gated?"],
          })
        return () => clearTimeout(timer);
      // 403 will happen if they're not logged in
    } else if (response.status === 403) {
      alert("Sign up and log in to bet!")
    } else {
      alert(resp.message);
    }
  };

  const toggleValid = () => {
    // if (loggedInState.status === "user.status/unauth" ||
    //     loggedInState.status === "user.status/active" ||
    //     loggedInState.status === "user.status/admin" ||
    //     loggedInState.status === "user.status/pending"
    // )
      if (!(loggedInState.status === null))
      {
          return betAmount === 0 ||
              betAmount > loggedInState.cash ||
              betWaitingForResp;
      } else  {
          return true;
      }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const numbers = /^[0-9]*$/;
    if (numbers.test(e.target.value)) {
      const amount = parseInt(e.target.value, 10) || 0;
      setBetAmount(amount);
    }
  };

  const renderOutcomeText = (result: string) => {
    if (result === "proposition.result/true" ) {
      return "Yes";
    } else if (result === "proposition.result/false") {
      return "No";
    } else if (result === "proposition.result/cancelled") {
      return "Cancelled"
    }
  };

  const renderPropositionText = () => {
    if (props.proposition["proposition/text"]) {
      return (<p>{props.proposition["proposition/text"]}</p>);
    } else if (props.prevProposition["proposition/text"]){
      return (
          <>
            <p>{"Last proposition: " + props.prevProposition["proposition/text"]}</p>
            <p>
              {"Outcome: " + renderOutcomeText(props.prevProposition["proposition/result"])}
            </p>
            <p>Next proposition soon!</p>
          </>
      );
    } else {
      return <p>Next proposition soon!</p>;
    }
  };

  const CTAtext = () => {
    if (loggedInState.status === null) {
      return "Sign up and log in to bet!"
    }
    else if (loggedInState.status === "user.status/pending") {
      return "Check your email and click on the link within to get more Whipcash!"
    }
  };

    const renderButtonContents = (defaultText:string) => {
      if (betWaitingForResp){
          return <div className="loading"/>
      } else if ((lastSuccessfulBetSide === true && defaultText === "Yes") ||
                 (lastSuccessfulBetSide === false && defaultText === "No")){
          return (
              <img src={baseUrl + "/img/logos/check-mark.svg"}
                   style={{height: "1rem", width: "1rem", marginTop: "0.25rem"}}
              />
          )
      } else {
          return <div>{defaultText}</div>
      }
  };

    const renderBettingOptions = () => {
        if (props.proposition["proposition/text"] &&
        props.proposition["proposition/betting-end-time"]){
      // if (moment().utc().isBefore(endBettingDate())) {
      if (props.proposition["proposition/betting-seconds-left"] > 0) {
        return (
            <>
              <p style={{fontSize: "0.75rem"}}>Timer: {props.proposition["proposition/betting-seconds-left"]}</p>
                <div className="form__group">
                    <label className="form__label" htmlFor="betAmount">Bet Amount</label>
                    <input
                        className="form__input"
                        value={betAmount > 0 ? betAmount : ""}
                        onChange={e => handleInputChange(e)}
                        // onKeyPress={e => betOnKeyPress(e)}
                        type="tel"
                        min="1"
                        name="betAmount"
                        id="betAmount"
                        autoComplete="off"
                        placeholder="Min. bet is 100"
                    />
                </div>
              <div className="form__button-group" style={{marginBottom: "0"}}>
                <button
                    className="button button--vote"
                    type="button"
                    key="Yes"
                    onClick={() => {
                      makePropBet(true)
                    }}>
                    {renderButtonContents("Yes")}
                </button>
                <button
                    className="button button--vote"
                    style={{marginRight: "0"}}
                    type="button"
                    key="No"
                    onClick={() => {
                      makePropBet(false)
                    }}>
                    {renderButtonContents("No")}
                </button>
              </div>
            </>
        );
      } else {
        return(<p>Bets are locked for this proposition!</p>);
      }
    }
  };

  const renderContent = () => {
      return (
          <div className="container" style={{paddingTop: "0"}}>
            <form className="form form--vote"
                  onSubmit={(e: any) => e.preventDefault()}
            >
                <fieldset className={props.noVideo? "form__fieldset form__fieldset_novideo" : "form__fieldset"}>
                    {/*TODO: remove inline style and pick proper color*/}
                    {CTAtext() &&
                        <p className="vote--flash">{CTAtext()}</p>
                    }
                    {renderPropositionText()}
                    {renderBettingOptions()}
                </fieldset>
            </form>
          </div>
      );
  };

  return (
    <div className={props.noVideo? "vote vote_novideo" : "vote"}>
      {renderContent()}
    </div>
  );
}
