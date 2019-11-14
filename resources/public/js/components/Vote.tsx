import React, { useState, useEffect, useContext, ChangeEvent } from "react";
import { Opponent, defaultTeam } from "./Home";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import { getCSRFToken, useInterval } from "../common";
import NumericInput from "react-numeric-input";

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [guessedTeamName, setGuessedTeamName] = useState<string | null>(null);
  const [betAmount, setBetAmount] = useState<number>(0);

  useEffect(() => {
    if (props.currentGame.id && props.matchID && loggedInState.userName) {
      getGuess();
    }
  }, [props.currentGame.id, props.matchID, loggedInState.userName]);

  const getGuess = async () => {
    const url =
      baseUrl +
      "user/guess" +
      "?match_id=" +
      props.matchID +
      "&game_id=" +
      props.currentGame.id;
    const response = await fetch(url, {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
      setGuessedTeamName(resp["team/name"]);
    } else if (response.status == 404) {
      setGuessedTeamName("");
    }
  };

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
    console.log(response.status);

    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
      setGuessedTeamName(props.team.teamName);
      // reset local state to no longer have a selected team
      props.setTeam(defaultTeam);
    }
  };

  const handleClick = (team: Opponent) => {
    props.setTeam(team);
  };

  const toggleValid = () => {
    // means the user hasn't select a team yet
    return props.team == defaultTeam || betAmount == 0 || betAmount > loggedInState.cash;
  };

  const renderContent = () => {
    if (loggedInState.userName) {
      if (props.passedGuessingPeriod === null || props.userStatus === null) {
        return <div>Loading</div>;
      } else if (!(props.userStatus == "user.status/active")) {
        return (
          <>
            <p>Verify your email to guess!</p>
          </>
        );
      } else if (!guessedTeamName && !props.passedGuessingPeriod) {
        return (
          <>
            <div>
              {props.opponents.map((opponent: Opponent) => {
                return (
                  <button
                    type="button"
                    key={opponent.teamID}
                    onClick={() => handleClick(opponent)}
                  >
                    {opponent.teamName}
                  </button>
                );
              })}
            </div>
            <h1> You selected {props.team.teamName}</h1>
            <NumericInput
              min={1}
              max={loggedInState.cash}
              value={betAmount}
              onChange={(valueAsNumber) => {
                setBetAmount(Number(valueAsNumber));
              }}
            />
            <button
              type="button"
              disabled={toggleValid()}
              onClick={() => makeGuess()}
            >
              Make Bet
            </button>
          </>
        );
      } else if (!guessedTeamName && props.passedGuessingPeriod) {
        return (
          <h3>
            Sorry! You missed guessing for this game. Stick around for the next
            one!
          </h3>
        );
      } else if (guessedTeamName && !props.passedGuessingPeriod) {
        return <h3>You guessed {guessedTeamName} for this game!</h3>;
      }
    } else {
      return <h3>Login to guess!</h3>;
    }
  };

  return <>{renderContent()}</>;
}
