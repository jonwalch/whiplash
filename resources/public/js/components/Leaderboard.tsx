import React, { useState, useEffect, ChangeEvent, useContext } from "react";
import "../../css/App.css";
import { Link } from "react-router-dom";
import { baseUrl } from "../config/const"
import { useInterval } from "../common";

export interface WeeklyLeader {
  user_name: string;
  payout: number;
}

export interface Leader {
  user_name: string;
  cash: number;
}

export function Leaderboard() {
  const [weeklyLeaderboard, setWeeklyLeaderboard] = useState<WeeklyLeader[]>([]);
  const [leaderboard, setLeaderboard] = useState<Leader[]>([]);

  useEffect(() => {
    getWeeklyLeaderboard();
    getLeaderboard();
  }, []);

  //every 5 minutes
  useInterval(() => {
    getWeeklyLeaderboard();
    getLeaderboard();
  }, 300000);

  const getLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/all-time", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    setLeaderboard(resp);
  };

  const renderLeaderboard = () => {
    return (
      <table className="leaderboard__table">
        <thead>
          <tr className="leaderboard__tr">
            <th className="leaderboard__th">User</th>
            <th className="leaderboard__th">Total Cash</th>
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

  const getWeeklyLeaderboard = async () => {
    const response = await fetch(baseUrl + "leaderboard/weekly", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    setWeeklyLeaderboard(resp);
  };

  const renderWeeklyLeaderboard = () => {
    if (weeklyLeaderboard.length == 0) {
      return <p>No payouts yet for this week!</p>;
    } else {
      return (
        <table className="leaderboard__table">
          <thead>
            <tr className="leaderboard__tr">
              <th className="leaderboard__th">User</th>
              <th className="leaderboard__th">Payout</th>
            </tr>
          </thead>
          <tbody>
            {weeklyLeaderboard.map((leader: WeeklyLeader) => {
              return (
                <tr className="leaderboard__tr" key={leader.user_name}>
                  <td className="leaderboard__td">{leader.user_name}</td>
                  <td className="leaderboard__td">{leader.payout}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      );
    }
  };

  return (
    <div className="leaderboard">
      <div className="container">
        <header>
          <h2>Leaderboard</h2>
        </header>
        <section className="leaderboard__section">
          <h3>All Time Top Ten</h3>
          {renderLeaderboard()}
        <section className="leaderboard__section">
        </section>
          <h3>Weekly Leaderboard</h3>
          {renderWeeklyLeaderboard()}
        </section>
      </div>
    </div>
  );
}
