import React, { useState, useEffect, useContext, ChangeEvent } from "react";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import {getUser} from "../common/getUser";
const { gtag } = require('ga-gtag');

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [betAmount, setBetAmount] = useState<number>(0);
  const [projectedResult, setProjectedResult] = useState<null | boolean>(null);

  const booleanToButton = () => {
    if (projectedResult == null) {
      return "none"
    }
    else if (projectedResult) {
      return 'Yes';
    }
    return 'No';
  };

  useEffect(() => {
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
  }, [projectedResult])

  const makePropBet = async () => {
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
      // setGuessedTeamName(props.team.teamName);
      alert(`You successfully bet $${betAmount} on outcome ${booleanToButton()}.`);
      // reset local state to no longer have a selected team
      setProjectedResult(null)
      // update user's cash
      getUser(setLoggedInState)
    } else {
      alert(resp);
    }
  };

  // const makeGuess = async () => {
  //   const response = await fetch(baseUrl + "user/guess", {
  //     headers: {
  //       "Content-Type": "application/json",
  //     },
  //     method: "POST",
  //     mode: "same-origin",
  //     redirect: "error",
  //     body: JSON.stringify({
  //       match_name: props.matchName,
  //       match_id: props.matchID,
  //       game_id: props.currentGame.id,
  //       team_name: props.team.teamName,
  //       team_id: props.team.teamID,
  //       bet_amount: betAmount,
  //     })
  //   });
  //
  //   if (props.isProduction) {
  //     // Trigger Google Analytics event
  //     gtag('event', 'bet', {
  //       event_category: 'Betting',
  //       event_label: loggedInState.userName,
  //       value: betAmount
  //     })
  //   }
  //
  //   const resp = await response.json();
  //   if (response.status == 200) {
  //     // setGuessedTeamName(props.team.teamName);
  //     alert(`You successfully bet $${betAmount} on ${props.team.teamName}`)
  //     // reset local state to no longer have a selected team
  //     props.setTeam(defaultTeam);
  //   } else {
  //     alert(resp);
  //   }
  // };
  //
  // function handleClick (team: Opponent) {
  //   props.setTeam(team)
  // };

  const toggleValid = () => {
    // means the user hasn't select a team yet
    return projectedResult == null || betAmount == 0 || betAmount > loggedInState.cash;
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const numbers = /^[0-9]*$/;
    if (numbers.test(e.target.value)) {
      const amount = parseInt(e.target.value, 10) || 0;
      setBetAmount(amount);
    }
  };

  const renderPropositionText = () => {
    if (props.propText) {
      return props.propText;
    } else {
      return "Next proposition soon!";
    }
  };

  const betOnKeyPress = (e: any) => {
    const key = e.key;
    if (key == "Enter" && !toggleValid()) {
      makePropBet();
    }
  };

  const renderBettingOptions = () => {

    if (props.propText != null){
      return (
          <>
            <div className="form__button-group">
              <button
                  className="button button--vote"
                  type="button"
                  key="Yes"
                  onClick={() => {
                    setProjectedResult(true)
                    // handleClick(opponent)
                  }}>
                Yes
              </button>
              <button
                  className="button button--vote"
                  type="button"
                  key="No"
                  onClick={() => {
                    setProjectedResult(false)
                    // handleClick(opponent)
                  }}>
                No
              </button>
              {/*{props.opponents.map((opponent: Opponent) => {*/}
              {/*  return (*/}
              {/*    <button*/}
              {/*      className="button button--vote"*/}
              {/*      type="button"*/}
              {/*      key={opponent.teamID}*/}
              {/*      onClick={() => {*/}
              {/*          handleClick(opponent)*/}
              {/*      }}>*/}
              {/*      {opponent.teamName}*/}
              {/*    </button>*/}
              {/*  );*/}
              {/*})}*/}
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
                className={"button button--make-bet " + (!toggleValid() ? "is-active": "")}
                type="button"
                disabled={toggleValid()}
                onClick={() => makePropBet()}
            >
              Make Bet
            </button>
          </>
      );
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
            <p className="vote__message"> {renderPropositionText()}</p>
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
                <p>{renderPropositionText()}</p>
                {renderBettingOptions()}
              </fieldset>
            </form>
          </div>
        );
      }
    } else {
      return (
        <div className="container">
          <p className="vote__message"> {renderPropositionText()}</p>
          <p className="vote__message">Login to participate!</p>
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
