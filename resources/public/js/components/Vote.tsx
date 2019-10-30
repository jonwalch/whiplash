import React, { useState, useEffect, useContext } from "react";
import { Opponent, defaultTeam, useInterval } from "./Home";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import { getCSRFToken } from "../common";

export function Vote(props: any) {
  const { state, setState } = useContext(LoginContext);
  const [hasGuessed, setHasGuessed] = useState(false);
  const [passedGuessingPeriod, setPastGuessingPeriod] = useState<boolean | null>(null);

  useEffect(() => {
    if (props.currentGame.id && props.matchID && state.userLoggedIn) {
      getGuess();
    }
  }, [props.currentGame.id, props.matchID, state.userLoggedIn]);

  const threeMinutes = 1000 * 60 * 3
  useInterval(() => {
    //TODO: case where begin_at is nil, just allow
    const beginAt: number = Date.parse(props.currentGame["begin_at"])
    if (beginAt + threeMinutes <= Date.now()) {
      setPastGuessingPeriod(true);
    } else {
      setPastGuessingPeriod(false);
    }
  }, 1000);

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
      setHasGuessed(true);

      const resp = await response.json();
      console.log(resp);
      console.log(response.status);
    } else if (response.status == 404) {
      setHasGuessed(false);
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
        team_id: props.team.teamID
      })
    });
    console.log(response.status);

    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
      setHasGuessed(true);
      // reset local state to no longer have a selected team
      props.setTeam(defaultTeam);
    }
  };

  const handleClick = (team: Opponent) => {
    props.setTeam(team);
  };

  const toggleValid = () => {
    // means the user hasn't select a team yet
    return props.team == defaultTeam;
  };

  const renderContent = () => {
    if (state.userLoggedIn && passedGuessingPeriod === null){
      return <p>Loading...</p>
    }
    else if (state.userLoggedIn && !hasGuessed && !passedGuessingPeriod) {
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
          <p> You selected {props.team.teamName}</p>
          <button
            type="button"
            disabled={toggleValid()}
            onClick={() => makeGuess()}
          >
            Make Guess
          </button>
        </>
      );
    } else if (state.userLoggedIn && !hasGuessed && passedGuessingPeriod) {
      return (
        <p>Sorry! You missed guessing for this game. Stick around for the next
          one!</p>
      );
    } else if (state.userLoggedIn && hasGuessed) {
      return <p>Guess submitted for this game!</p>;
    } else {
      return <p>Login to guess!</p>;
    }
  };

  return <>{renderContent()}</>;
}
