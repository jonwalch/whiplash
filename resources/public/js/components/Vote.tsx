import React, { useState, useEffect, useContext } from "react";
import { Opponent, defaultTeam } from "./Home";
import { LoginContext } from "../contexts/LoginContext";

export function Vote(props: any) {
  const { state, setState } = useContext(LoginContext);
  const [canGuess, setCanGuess] = useState(false);

  useEffect(() => {
    if (props.currentGame.id && props.matchID && state.userLoggedIn) {
      getGuess();
    }
  }, [props.currentGame.id, props.matchID, state.userLoggedIn]);

  const getGuess = async () => {
    const url =
      "http://localhost:3000/v1/user/guess" +
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
      setCanGuess(false);

      const resp = await response.json();
      console.log(resp);
      console.log(response.status);
    } else if (response.status == 404) {
      setCanGuess(true);
    }
  };

  const makeGuess = async () => {
    const response = await fetch("http://localhost:3000/v1/user/guess", {
      headers: { "Content-Type": "application/json" },
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
      setCanGuess(false);
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
    if (state.userLoggedIn && canGuess) {
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
          <button
            type="button"
            disabled={toggleValid()}
            onClick={() => makeGuess()}
          >
            Make Guess
          </button>
        </>
      );
    } else if (state.userLoggedIn) {
      return <h3>Guess submitted for this game!</h3>;
    } else {
      return <h3>Login to guess!</h3>;
    }
  };

  return <>{renderContent()}</>;
}
