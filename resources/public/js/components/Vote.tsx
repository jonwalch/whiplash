import React, { useState, useEffect, useContext, ChangeEvent } from "react";
import { Opponent, defaultTeam } from "./Home";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import { getCSRFToken, useInterval } from "../common";

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  // const [guessedTeamName, setGuessedTeamName] = useState<string | null>(null);
  const [betAmount, setBetAmount] = useState<number>(0);

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
      // reset local state to no longer have a selected team
      props.setTeam(defaultTeam);
      alert(`You successfully bet $${betAmount} on ${props.team.teamName}`)
    } else {
      alert(resp);
    }
  };

  const handleClick = (team: Opponent) => {
    props.setTeam(team);
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
      return <p>You selected {props.team.teamName}</p>;
    } else {
      return <p>Select a team!</p>;
    }
  };

  const renderContent = () => {
    if (loggedInState.userName) {
      if (props.userStatus === null) {
        return <p>Loading...</p>;
      } else if (!(props.userStatus == "user.status/active")) {
        return (
          <>
            <p>Verify your email to bet!</p>
          </>
        );
      } else {
        return (
          <>
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
            {renderTeamSelect()}
            <input
              value={betAmount > 0 ? betAmount : ""}
              onChange={e => {
                handleInputChange(e);
              }}
              type="number"
              name="betAmount"
              id="betAmount"
            />
            <button
              className="button"
              type="button"
              disabled={toggleValid()}
              onClick={() => makeGuess()}
            >
              Make Bet
            </button>
          </>
        );
      }
    } else {
      return <p>Login to guess!</p>;
    }
  };

  return (
    <div className="vote">
      <div className="container">
      {renderContent()}
      </div>
    </div>
  );
}
