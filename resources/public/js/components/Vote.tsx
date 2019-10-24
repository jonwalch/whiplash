import React, { useState, useEffect, useContext } from "react";
import { Opponent } from "./Home";
import { LoginContext } from "../contexts/LoginContext";

//TODO don't allow voting if one exists already
export function Vote(props: any) {
  const { state, setState } = useContext(LoginContext);

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
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
  };

  const handleClick = (team: Opponent) => {
    props.setTeam(team);
  };

  const toggleValid = () => {
    // -1 means the user hasn't select a team yet
    return props.team.teamID == -1;
  };

  const renderContent = () => {
    if (state.userLoggedIn) {
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
    } else {
      return <h3>Login to guess!</h3>;
    }
  };

  return <>{renderContent()}</>;
}
