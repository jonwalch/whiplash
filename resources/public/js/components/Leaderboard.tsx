import React, { useState, useEffect, useContext } from "react";
import "../../css/App.css";
import { baseUrl } from "../config/const"
import { useInterval } from "../common";
import {LoginContext} from "../contexts/LoginContext";
import {failedToFetch} from "./Home";

export interface EventScore {
  user_name: string;
  score: number;
}

export interface Leader {
  user_name: string;
  cash: number;
}

export function Leaderboard(props:any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [leaderboard, setLeaderboard] = useState<Leader[]>([]);

  useEffect(() => {
    getEventScoreLeaderboard();
    getLeaderboard();
  }, [loggedInState.cash, props.proposition]);

  useInterval(() => {
    getEventScoreLeaderboard();
    getLeaderboard();
  }, 5000);

  const getLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/all-time", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      setLeaderboard(resp);
    }
  };

  const renderLeaderboard = () => {
    return (
      <table className="leaderboard__table">
        <thead className="leaderboard__thead">
          <tr className="leaderboard__tr">
            <th className="leaderboard__th">User</th>
            <th className="leaderboard__th">Total Whipcash</th>
          </tr>
        </thead>
        <tbody>
          {leaderboard.map((leader: Leader) => {
            return (
              <tr className="leaderboard__tr" key={leader.user_name}>
                <td className="leaderboard__td">{leader.user_name}</td>
                <td className="leaderboard__td">${leader.cash}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    );
  };

  const getEventScoreLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/event", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      props.setEventScoreLeaderboard(resp);
    }
  };

  const renderEventScoreLeaderboard = () => {
    return (
        <div className="leaderboard__table-container">
          <table className="leaderboard__table">
            <thead className="leaderboard__thead">
            <tr className="leaderboard__tr">
              <th className="leaderboard__th">User</th>
              <th className="leaderboard__th">Score</th>
            </tr>
            </thead>
            <tbody>
            {props.eventScoreLeaderboard.map((leader: EventScore) => {
              return (
                  <tr className="leaderboard__tr" key={leader.user_name}>
                    <td className="leaderboard__td">{leader.user_name}</td>
                    <td className="leaderboard__td">{leader.score}</td>
                  </tr>
              );
            })}
            </tbody>
          </table>
        </div>
    );
  };

  const scoreText = () => {
    if (props.twitchUsername == failedToFetch) {
      return "Last"
    }
    return "Live"
  };

  return (
    <div className="leaderboard">
      <div className="container leaderboard__container">
        <header className="leaderboard__header leaderboard__header--primary">
          <h2 className="leaderboard__title">Leaderboard</h2>
        </header>
        <section className="leaderboard__section">
          <header className="leaderboard__header">
            <h3 className="leaderboard__subtitle">All Time Top Ten</h3>
          </header>
          {renderLeaderboard()}
        </section>
        <section className="leaderboard__section">
          <header className="leaderboard__header">
            <h3 className="leaderboard__subtitle"> {scoreText()} Event Scores</h3>
          </header>
          {renderEventScoreLeaderboard()}
        </section>
      </div>
    </div>
  );
}
