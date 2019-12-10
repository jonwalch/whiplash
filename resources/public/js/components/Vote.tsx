import React, { useState, useEffect, useContext, ChangeEvent } from "react";
import { Opponent, defaultTeam } from "./Home";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import { getCSRFToken, useInterval } from "../common";

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  // const [guessedTeamName, setGuessedTeamName] = useState<string | null>(null);
  const [betAmount, setBetAmount] = useState<number>(0);

  useEffect(() => {
    const buttons = document.querySelectorAll('.button--vote');

    buttons.forEach((button) => {
      if (button.innerHTML === props.team.teamName) {
        // button text does match teamName
        if (!button.classList.contains('is-active')) {
          button.classList.add('is-active')
        }
      } else {
        // button text does not match teamName
        button.classList.remove('is-active')
      }
    })
  }, [props.team.teamName])

  // useEffect(() => {
  //   if (props.currentGame.id && props.matchID && loggedInState.userName) {
  //     getGuess();
  //   }
  // }, [props.currentGame.id, props.matchID, loggedInState.userName]);
  //
  // const getGuess = async () => {
  //   const url =
  //     baseUrl +
  //     "user/guess" +
  //     "?match_id=" +
  //     props.matchID +
  //     "&game_id=" +
  //     props.currentGame.id;
  //   const response = await fetch(url, {
  //     headers: { "Content-Type": "application/json" },
  //     method: "GET",
  //     mode: "same-origin",
  //     redirect: "error"
  //   });
  //   if (response.status == 200) {
  //     const resp = await response.json();
  //     setGuessedTeamName(resp["team/name"]);
  //   } else if (response.status == 404) {
  //     setGuessedTeamName("");
  //   }
  // };

  const makeGuess = async () => {
    const response = await fetch(baseUrl + "user/guess", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error",
      body: JSON.stringify({
        match_name: props.matchName,
        match_id: props.matchID,
        game_id: props.currentGame.id,
        team_name: props.team.teamName,
        team_id: props.team.teamID,
        bet_amount: betAmount,
      })
    });

    const resp = await response.json();
    if (response.status == 200) {
      // setGuessedTeamName(props.team.teamName);
      alert(`You successfully bet $${betAmount} on ${props.team.teamName}`)
      // reset local state to no longer have a selected team
      props.setTeam(defaultTeam);
    } else {
      alert(resp);
    }
  };

  function handleClick (team: Opponent) {
    props.setTeam(team)
  };

  const toggleValid = () => {
    // means the user hasn't select a team yet
    return props.team == defaultTeam || betAmount == 0 || betAmount > loggedInState.cash;
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    e.preventDefault();
    const numbers = /^[0-9]*$/;
    if (numbers.test(e.target.value)) {
      const amount = parseInt(e.target.value, 10) || 0;
      setBetAmount(amount);
    } 
  };

  const renderTeamSelect = () => {
    if (props.team.teamName) {
      return <p>You selected <strong>{props.team.teamName}</strong></p>;
    } else {
      return <p>Select a team!</p>;
    }
  };

  const renderContent = () => {
    if (loggedInState.userName) {
      if (props.userStatus === null) {
        return (
          <div className="container">
            <p>Loading...</p>
          </div>
        );
      } else if (!(props.userStatus == "user.status/active")) {
        return (
          <div className="container">
            <p className="vote__message">Verify your email to bet!</p>
          </div>
        );
      } else {
        return (
          <div className="container">
            <form className="form form--vote">
              <fieldset className="form__fieldset">
                {renderTeamSelect()}
                <div className="form__button-group">
                  {props.opponents.map((opponent: Opponent) => {
                    return (
                      <button
                        className="button button--vote"
                        type="button"
                        key={opponent.teamID}
                        onClick={() => {
                            handleClick(opponent)
                        }}>
                        {opponent.teamName}
                      </button>
                    );
                  })}
                </div>
                <div className="form__group">
                  <label className="form__label" htmlFor="betAmount">Bet Amount</label>
                  <input
                    className="form__input"
                    value={betAmount > 0 ? betAmount : ""}
                    onChange={e => {
                      handleInputChange(e);
                    }}
                    type="number"
                    min="1"
                    name="betAmount"
                    id="betAmount"
                  />
                </div>
                <button
                    className="button"
                    type="button"

                    disabled={toggleValid()}
                    onClick={() => makeGuess()}
                >
                  Make Bet
                </button>
              </fieldset>
            </form>
          </div>
        );
      }
    } else {
      return (
        <div className="container">
          <p className="vote__message">Login to bet!</p>
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
