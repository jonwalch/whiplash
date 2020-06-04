import React, { useState, useEffect, useContext } from "react";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
const { gtag } = require('ga-gtag');

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [betAmount, setBetAmount] = useState<number>(0);
  const [projectedResult, setProjectedResult] = useState<null | boolean>(null);
  // const [secondsLeftToBet, setSecondsLeftToBet] = useState<number>(0);
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

  // useInterval(() => {
  //       if (props.proposition["proposition/betting-end-time"]) {
  //         setSecondsLeftToBet(calculateSecondsLeftToBet())
  //       }}, 1000);
  //
  // useEffect(() => {
  //   setSecondsLeftToBet(calculateSecondsLeftToBet())
  // }, [props.proposition]);

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

  // const endBettingDate = () => {
  //   if (props.proposition["proposition/betting-end-time"]){
  //     return moment(props.proposition["proposition/betting-end-time"],
  //         "YYYY-MM-DDTHH:mm:ssZ");
  //   } else {
  //     return Infinity;
  //   }
  // };

  // const calculateSecondsLeftToBet = () => {
  //   return moment(props.proposition["proposition/betting-end-time"], "YYYY-MM-DDTHH:mm:ssZ")
  //       .diff(moment().utc(), "seconds");
  // };

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

    // Trigger Google Analytics event
    gtag('event', 'prop-bet', {
      event_category: 'Betting',
      event_label: loggedInState.userName,
      value: betAmount
    });

    const resp = await response.json();
    setProjectedResult(null);
    setBetWaitingForResp(false);
    if (response.status == 200) {
      alert(`You successfully bet $${betAmount} on outcome ${booleanToButton()}.`);
      // update user's cash
      setLoggedInState(
          { userName: loggedInState.userName,
            status: loggedInState.status,
            cash: loggedInState.cash - betAmount,
            notifications: loggedInState.notifications})
      // 403 will happen if they're not logged in AND they aren't sending the google analytics cookie.
    } else if (response.status === 403) {
      alert("Sign up or disable your ad blocker to bet!")
    } else {
      alert(resp.message);
    }
  };

  const toggleValid = () => {
    if (loggedInState.status === "user.status/unauth" ||
        loggedInState.status === "user.status/active" ||
        loggedInState.status === "user.status/admin")
    {
      return projectedResult == null ||
          betAmount == 0 ||
          betAmount > loggedInState.cash ||
          betWaitingForResp;

    } else if (loggedInState.status === "user.status/pending") {
      return true;

    } else {
      return projectedResult == null ||
          betAmount == 0 ||
          // TODO: change this to a constant
          betAmount > 500 ||
          betWaitingForResp;
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
    } else {
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

  const betOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      makePropBet();
    }
  };

  const renderCTA = () => {
    if (loggedInState.status === null) {
      return "You can bet up to $500 Whipcash!"
    }
    else if (loggedInState.status === "user.status/unauth") {
      return "Sign up to receive bailouts when you drop below $100 Whipcash!"
    }
    else if (loggedInState.status == "user.status/pending") {
      return "Verify your email to bet!"
    }
  };

  const renderBettingOptions = () => {
    if (props.proposition["proposition/text"] &&
        props.proposition["proposition/betting-end-time"]){
      // if (moment().utc().isBefore(endBettingDate())) {
      if (props.proposition["proposition/betting-seconds-left"] > 0) {
        return (
            <>
              <p>Seconds left to bet: {props.proposition["proposition/betting-seconds-left"]}</p>
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
              {/*TODO: remove inline style and pick proper color*/}
              <p style={{color: "red"}}>{renderCTA()}</p>
              <button
                  className={"button button--make-bet " + (!toggleValid() ? "is-active" : "")}
                  type="button"
                  disabled={toggleValid()}
                  onClick={() => makePropBet()}
              >
                <div className={betWaitingForResp ? "loading" : ""}>
                  {betWaitingForResp ? "" : "Make Bet"}
                </div>
              </button>
            </>
        );
      } else {
        return(<p>Bets are locked for this proposition!</p>);
      }
    }
  };

  const renderContent = () => {
      return (
          <div className="container">
            <form className="form form--vote"
                  onSubmit={(e: any) => e.preventDefault()}
            >
              <fieldset className="form__fieldset">
                {renderPropositionText()}
                {renderBettingOptions()}
              </fieldset>
            </form>
          </div>
      );
  };

  return (
    <div className="vote">
      {renderContent()}
    </div>
  );
}
