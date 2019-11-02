import React, { useState, useEffect, useContext } from "react";
import { Opponent, defaultTeam } from "./Home";
import { LoginContext } from "../contexts/LoginContext";
import { baseUrl } from "../config/const";
import { getCSRFToken, useInterval } from "../common";

export function Vote(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [passedGuessingPeriod, setPastGuessingPeriod] = useState<boolean | null>(null);
  const [guessedTeamName, setGuessedTeamName] = useState<string | null>(null);
  const [userStatus, setUserStatus] = useState<string | null>(null);

  useEffect(() => {
    if (props.currentGame.id && props.matchID && loggedInState.userName) {
      getGuess();
    }
  }, [props.currentGame.id, props.matchID, loggedInState.userName]);

  useEffect(() => {
    if (loggedInState.userName) {
      getUser();
    }
  }, [loggedInState.userName]);

  const threeMinutes = 1000 * 60 * 3;
  useInterval(() => {
    //Allows if begin_at is null
    const beginAt: number = Date.parse(props.currentGame["begin_at"]);
    if (beginAt + threeMinutes <= Date.now()) {
      setPastGuessingPeriod(true);
    } else {
      setPastGuessingPeriod(false);
    }
  }, 1000);

  const getUser = async () => {
    const response = await fetch(baseUrl + "user", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      // console.log(resp)
      setUserStatus(resp["user/status"]);
    } else {
      setUserStatus("");
    }
  };

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
      // console.log(resp);
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
        team_id: props.team.teamID
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
    return props.team == defaultTeam;
  };

  const renderContent = () => {
    if (loggedInState.userName) {
      if (passedGuessingPeriod === null || userStatus === null) {
        return <div>Loading</div>;
      } else if (!(userStatus == "user.status/active")) {
        return (
          <>
            <p>Verify your email to guess!</p>
          </>
        );
      } else if (!guessedTeamName && !passedGuessingPeriod) {
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
      } else if (!guessedTeamName && passedGuessingPeriod) {
        return (
          <h3>
            Sorry! You missed guessing for this game. Stick around for the next
            one!
          </h3>
        );
      } else if (guessedTeamName && !passedGuessingPeriod) {
        return <h3>You guessed {guessedTeamName} for this game!</h3>;
      }
    } else {
      return <h3>Login to guess!</h3>;
    }
  };

  return <>{renderContent()}</>;
}
