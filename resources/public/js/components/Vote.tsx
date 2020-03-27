import React, { useState, useEffect, useContext } from "react";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import {getUser} from "../common/getUser";
import {useInterval} from "../common";
import moment from "moment";
const { gtag } = require('ga-gtag');

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [betAmount, setBetAmount] = useState<number>(0);
  const [projectedResult, setProjectedResult] = useState<null | boolean>(null);
  const [secondsLeftToBet, setSecondsLeftToBet] = useState<number>(0);
  const [betWaitingForResp, setBetWaitingForResp] = useState<boolean>(false);

  const booleanToButton = () => {
    if (projectedResult == null) {
      return "none"
    }
    else if (projectedResult) {
      return 'Yes';
    }
    return 'No';
  };

  useInterval(() => {
        if (props.proposition["proposition/betting-end-time"]) {
          setSecondsLeftToBet(calculateSecondsLeftToBet())
        }}, 1000);

  useEffect(() => {
    setSecondsLeftToBet(calculateSecondsLeftToBet())
  }, [props.proposition]);

  useEffect(() => {
    // TODO: Refactor and use useRef
    const buttons = document.querySelectorAll('.button--vote');

    buttons.forEach((button) => {
      if (button.innerHTML == booleanToButton()) {
        // button text does match selection
        if (!button.classList.contains('is-active')) {
          button.classList.add('is-active')
        }
      } else {
        // button text does not match selection
        button.classList.remove('is-active')
      }
    })
  }, [projectedResult]);

  const endBettingDate = () => {
    if (props.proposition["proposition/betting-end-time"]){
      return moment(props.proposition["proposition/betting-end-time"],
          "YYYY-MM-DDTHH:mm:ssZ");
    } else {
      return Infinity;
    }
  };

  const calculateSecondsLeftToBet = () => {
    return moment(props.proposition["proposition/betting-end-time"], "YYYY-MM-DDTHH:mm:ssZ")
        .diff(moment().utc(), "seconds");
  };

  const makePropBet = async () => {
    setBetWaitingForResp(true);
    const response = await fetch(baseUrl + "user/prop-bet", {
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

    if (props.isProduction) {
      // Trigger Google Analytics event
      gtag('event', 'prop-bet', {
        event_category: 'Betting',
        event_label: loggedInState.userName,
        value: betAmount
      })
    }

    const resp = await response.json();
    if (response.status == 200) {
      alert(`You successfully bet $${betAmount} on outcome ${booleanToButton()}.`);
      // update user's cash
      setLoggedInState(
          { userName: loggedInState.userName,
            status: loggedInState.status,
            cash: loggedInState.cash - betAmount,
            notifications: loggedInState.notifications})
    } else {
      alert(resp.message);
    }
    setProjectedResult(null);
    setBetWaitingForResp(false);
  };

  const toggleValid = () => {
    // means the user hasn't select a team yet
    return projectedResult == null ||
        betAmount == 0 ||
        betAmount > loggedInState.cash ||
        betWaitingForResp;
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const numbers = /^[0-9]*$/;
    if (numbers.test(e.target.value)) {
      const amount = parseInt(e.target.value, 10) || 0;
      setBetAmount(amount);
    }
  };

  const renderPropositionText = (useClass : boolean) => {
    let nameOfClass = "vote__message";
    if (props.proposition["proposition/text"]) {
      return (<p className={useClass ? nameOfClass : ""}>{props.proposition["proposition/text"]}</p>);
    } else if (props.prevProposition["proposition/text"]){
      return (
          <>
            <p className={useClass ? nameOfClass : ""}>
              {"Last proposition: " + props.prevProposition["proposition/text"]}
            </p>
            <p className={useClass ? nameOfClass : ""}>
              {"Outcome: " + (props.prevProposition["proposition/result?"] ? "Yes" : "No")}
            </p>
            <p className={useClass ? nameOfClass : ""}>Next proposition soon!</p>
          </>
      );
    } else {
      return <p className={useClass ? nameOfClass : ""}>Next proposition soon!</p>;
    }
  };

  const betOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      makePropBet();
    }
  };

  const renderBettingOptions = () => {
    if (props.proposition["proposition/text"] &&
        props.proposition["proposition/betting-end-time"]){
      if (moment().utc().isBefore(endBettingDate())) {
        return (
            <>
              <p>Seconds left to bet: {secondsLeftToBet}</p>
              <div className="form__button-group">
                <button
                    className="button button--vote"
                    type="button"
                    key="Yes"
                    onClick={() => {
                      setProjectedResult(true)
                    }}>
                  Yes
                </button>
                <button
                    className="button button--vote"
                    type="button"
                    key="No"
                    onClick={() => {
                      setProjectedResult(false)
                    }}>
                  No
                </button>
              </div>
              <div className="form__group">
                <label className="form__label" htmlFor="betAmount">Bet Amount</label>
                <input
                    className="form__input"
                    value={betAmount > 0 ? betAmount : ""}
                    onChange={e => {
                      handleInputChange(e);
                    }}
                    onKeyPress={e => betOnKeyPress(e)}
                    type="text"
                    pattern="/^[0-9]*$/"
                    min="1"
                    name="betAmount"
                    id="betAmount"
                />
              </div>
              <button
                  className={"button button--make-bet " + (!toggleValid() ? "is-active" : "")}
                  type="button"
                  disabled={toggleValid()}
                  onClick={() => makePropBet()}
              >
                Make Bet
              </button>
            </>
        );
      } else {
        return(<p>Bets are locked for this proposition!</p>);
      }
    }
  };

  const renderContent = () => {
    if (loggedInState.userName) {
      if (loggedInState.status === null) {
        return (
            <div className="container">
              <p>Loading...</p>
            </div>
        );
      } else if (loggedInState.status == "user.status/pending") {
        return (
          <div className="container">
            {renderPropositionText(true)}
            <p className="vote__message">Verify your email to participate!</p>
          </div>
        );
      } else {
        return (
          <div className="container">
            <form className="form form--vote"
                  onSubmit={(e: any) => e.preventDefault()}
            >
              <fieldset className="form__fieldset">
                {renderPropositionText(false)}
                {renderBettingOptions()}
              </fieldset>
            </form>
          </div>
        );
      }
    } else {
      return (
        <div className="container">
          {renderPropositionText(true)}
          <p className="vote__message">Log in to participate!</p>
        </div>
      );
    }
  };

  return (
    <div className="vote">
      {renderContent()}
    </div>
  );
}
